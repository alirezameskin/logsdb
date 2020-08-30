package logsdb.storage

import cats.MonadError
import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import cats.implicits._
import fs2.Pull
import org.rocksdb.{Options, RocksIterator}
import org.{rocksdb => jrocks}

import scala.concurrent.duration.FiniteDuration
import scala.util.Try

trait RocksDB[F[_]] {
  def get(key: Array[Byte]): F[Option[Array[Byte]]]

  def put(key: Array[Byte], value: Array[Byte]): F[Unit]

  def get[K, V](key: K)(implicit K: Encoder[K], V: Decoder[V]): F[Option[V]]

  def put[K, V](key: K, value: V)(implicit K: Encoder[K], V: Encoder[V]): F[Unit]

  def startsWith[K, V](prefix: K)(implicit KE: Encoder[K], KD: Decoder[K], V: Decoder[V]): fs2.Stream[F, V]

  def tail(chunkSize: Int, delay: FiniteDuration, from: Option[Array[Byte]]): Pull[F, Array[Byte], Option[Array[Byte]]]
}

object RocksDB {

  def open[F[_]: Sync: ContextShift: Timer](path: String, blocker: Blocker)(
    implicit M: MonadError[F, Throwable]
  ): Resource[F, RocksDB[F]] = {
    val options = new Options()
      .useFixedLengthPrefixExtractor(8)
      .setCreateIfMissing(true)

    val acquire: F[jrocks.RocksDB] = for {
      _ <- M.fromTry(Try(jrocks.RocksDB.loadLibrary()))
      d <- M.fromTry(Try(jrocks.RocksDB.open(options, path)))
    } yield d

    Resource.fromAutoCloseable(acquire).map(d => new RocksDBImpl[F](d, blocker))

  }
}
