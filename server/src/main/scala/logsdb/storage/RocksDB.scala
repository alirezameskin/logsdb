package logsdb.storage

import cats.MonadError
import cats.effect.{Resource, Sync}
import cats.implicits._
import org.rocksdb.Options
import org.{rocksdb => jrocks}

import scala.util.Try

trait RocksDB[F[_]] {
  def get(key: Array[Byte]): F[Option[Array[Byte]]]

  def put(key: Array[Byte], value: Array[Byte]): F[Unit]

  def get[K, V](key: K)(implicit K: Encoder[K], V: Decoder[V]): F[Option[V]]

  def put[K, V](key: K, value: V)(implicit K: Encoder[K], V: Encoder[V]): F[Unit]

  def startsWith[K, V](prefix: K)(implicit KE: Encoder[K], KD: Decoder[K], V: Decoder[V]): F[Iterator[V]]

  def endsWith[K, V](prefix: K)(implicit K: Encoder[K], V: Decoder[V]): F[Iterator[V]]
}

object RocksDB {

  def open[F[_]: Sync](path: String)(implicit M: MonadError[F, Throwable]): Resource[F, RocksDB[F]] = {
    val options = new Options()
      .useFixedLengthPrefixExtractor(8)
      .setCreateIfMissing(true)

    val acquire: F[jrocks.RocksDB] = for {
      _ <- M.fromTry(Try(jrocks.RocksDB.loadLibrary()))
      d <- M.fromTry(Try(jrocks.RocksDB.open(options, path)))
    } yield d

    Resource.fromAutoCloseable(acquire).map(d => new RocksDBImpl[F](d))
  }
}
