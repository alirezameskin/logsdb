package logsdb.storage

import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Concurrent, ContextShift, Resource, Sync, Timer}
import cats.implicits._
import logsdb.protos.Collection
import logsdb.settings.StorageSettings
import org.rocksdb._
import org.{rocksdb => jrocks}

import scala.jdk.CollectionConverters._
import scala.util.Try

trait RocksDB[F[_]] {

  def get(collection: String, key: Array[Byte]): F[Option[Array[Byte]]]

  def put(collection: String, key: Array[Byte], value: Array[Byte]): F[Unit]

  def startsWith(collection: String, prefix: Array[Byte]): fs2.Stream[F, (Array[Byte], Array[Byte])]

  def collections: F[Seq[Collection]]
}

object RocksDB {

  val DEFAULT_COLUMN_FAMILY = "default".getBytes

  def open[F[_]: ContextShift: Timer: Concurrent](settings: StorageSettings): Resource[F, RocksDB[F]] = {
    val options = new DBOptions()
      .setCreateIfMissing(true)

    val columnFamilyOptions = new ColumnFamilyOptions()
      .useFixedLengthPrefixExtractor(8)

    val acquire: F[(jrocks.RocksDB, Map[String, ColumnFamilyHandle])] = for {
      _         <- Try(jrocks.RocksDB.loadLibrary()).liftTo[F]
      available <- Try(jrocks.RocksDB.listColumnFamilies(new Options(), settings.path).asScala.toList).liftTo[F]
      families    = if (available.contains(DEFAULT_COLUMN_FAMILY)) available else available.appended(DEFAULT_COLUMN_FAMILY)
      descriptors = families.map(name => new ColumnFamilyDescriptor(name, columnFamilyOptions)).asJava
      list        = scala.collection.mutable.ListBuffer.empty[ColumnFamilyHandle].asJava
      db <- Try(jrocks.RocksDB.open(options, settings.path, descriptors, list)).liftTo[F]
      handles = list.asScala.map(h => (new String(h.getName), h)).toMap
    } yield (db, handles)

    for {
      _         <- Resource.liftF(Try(jrocks.RocksDB.loadLibrary()).liftTo[F])
      semaphore <- Resource.liftF(Semaphore[F](1))
      resource  <- Resource.make(acquire)(r => Sync[F].delay { r._1.close() })
      handles   <- Resource.liftF(Ref.of(resource._2))
    } yield new RocksDBImpl[F](resource._1, handles, semaphore)

  }

}
