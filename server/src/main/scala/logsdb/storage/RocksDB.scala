package logsdb.storage

import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Blocker, Concurrent, ContextShift, Resource, Sync, Timer}
import cats.implicits._
import fs2.Pull
import org.rocksdb._
import org.{rocksdb => jrocks}

import scala.concurrent.duration.FiniteDuration
import scala.jdk.CollectionConverters._
import scala.util.Try

trait RocksDB[F[_]] {

  def createCollection(collection: String): F[Unit]

  def get(collection: String, key: Array[Byte]): F[Option[Array[Byte]]]

  def put(collection: String, key: Array[Byte], value: Array[Byte]): F[Unit]

  def get[K, V](collection: String, key: K)(implicit K: Encoder[K], V: Decoder[V]): F[Option[V]]

  def put[K, V](collection: String, key: K, value: V)(implicit K: Encoder[K], V: Encoder[V]): F[Unit]

  def startsWith[K, V](collection: String, prefix: K)(implicit KE: Encoder[K], KD: Decoder[K], V: Decoder[V]): fs2.Stream[F, V]

  def tail(
    collection: String,
    chunkSize: Int,
    delay: FiniteDuration,
    from: Option[Array[Byte]]
  ): Pull[F, Array[Byte], Option[Array[Byte]]]
}

object RocksDB {

  val DEFAULT_COLUMN_FAMILY = "default".getBytes

  def open[F[_]: ContextShift: Timer: Concurrent](path: String, blocker: Blocker): Resource[F, RocksDB[F]] = {
    val options = new DBOptions()
      .setCreateIfMissing(true)

    val columnFamilyOptions = new ColumnFamilyOptions()
      .useFixedLengthPrefixExtractor(8)

    val acquire: F[(jrocks.RocksDB, Map[String, ColumnFamilyHandle])] = for {
      _         <- Try(jrocks.RocksDB.loadLibrary()).liftTo[F]
      available <- Try(jrocks.RocksDB.listColumnFamilies(new Options(), path).asScala.toList).liftTo[F]
      families    = if (available.contains(DEFAULT_COLUMN_FAMILY)) available else available.appended(DEFAULT_COLUMN_FAMILY)
      descriptors = families.map(name => new ColumnFamilyDescriptor(name, columnFamilyOptions)).asJava
      list        = scala.collection.mutable.ListBuffer.empty[ColumnFamilyHandle].asJava
      db <- Try(jrocks.RocksDB.open(options, path, descriptors, list)).liftTo[F]
      handles = list.asScala.map(h => (new String(h.getName), h)).toMap
    } yield (db, handles)

    for {
      _         <- Resource.liftF(Try(jrocks.RocksDB.loadLibrary()).liftTo[F])
      semaphore <- Resource.liftF(Semaphore[F](1))
      resource  <- Resource.make(acquire)(r => Sync[F].delay { r._1.close() })
      handles   <- Resource.liftF(Ref.of(resource._2))
    } yield new RocksDBImpl[F](resource._1, handles, blocker, semaphore)

  }

}
