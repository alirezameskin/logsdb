package logsdb.storage

import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import cats.implicits._
import cats.{Applicative, MonadError}
import fs2.{Pull, RaiseThrowable, Stream}
import org.rocksdb.RocksIterator
import org.{rocksdb => jrocks}
import logsdb.implicits._

import scala.concurrent.duration.FiniteDuration

class RocksDBImpl[F[_]: Sync: ContextShift: Timer: RaiseThrowable](db: jrocks.RocksDB, blocker: Blocker)(
  implicit M: MonadError[F, Throwable]
) extends RocksDB[F] {

  type Key   = Array[Byte]
  type Value = Array[Byte]

  def get(key: Array[Byte]): F[Option[Array[Byte]]] = Applicative[F].pure(Option(db.get(key)))

  def put(key: Array[Byte], value: Array[Byte]): F[Unit] = db.put(key, value).pure[F]

  private def encodeValue[V](value: Option[Array[Byte]])(implicit D: Decoder[V]): Option[V] = value match {
    case Some(v) => D.decode(v).toOption
    case None    => None
  }

  def get[K, V](key: K)(implicit K: Encoder[K], V: Decoder[V]): F[Option[V]] =
    for {
      bytes <- M.fromEither(K.encode(key))
      res   <- this.get(bytes).map(b => encodeValue[V](b))
    } yield res

  def put[K, V](key: K, value: V)(implicit K: Encoder[K], V: Encoder[V]): F[Unit] =
    for {
      key   <- M.fromEither(K.encode(key))
      value <- M.fromEither(V.encode(value))
      _     <- this.put(key, value)
    } yield ()

  def startsWith[K, V](prefix: K)(implicit KE: Encoder[K], KD: Decoder[K], VD: Decoder[V]): Stream[F, V] = {
    val stream = for {
      iterator <- Stream.resource(makeIterator)
      bytes    <- Stream.fromEither(KE.encode(prefix))
      _ = iterator.seek(bytes)
      stream <- iterator.valuesStream[F]
    } yield stream

    stream.map(v => VD.decode(v)).map(_.toOption).filter(_.isDefined).map(_.get)
  }

  override def tail(chunkSize: Int, pullDelay: FiniteDuration, lastOffset: Option[Key]): Pull[F, Value, Option[Key]] = {
    val stream = lastOffset match {
      case Some(offset) =>
        for {
          iterator <- Stream.resource(makeIterator)
          _ = iterator.seek(offset)
          _ = iterator.next()
          str <- iterator.stream[F]
        } yield str
      case None =>
        for {
          iterator <- Stream.resource(makeIterator)
          _ = iterator.seekToLast()
          str <- iterator.stream[F]
        } yield str
    }

    pullStream(stream, chunkSize, lastOffset).flatMap { offset =>
      Pull.eval(Timer[F].sleep(pullDelay)) >> tail(chunkSize, pullDelay, offset)
    }
  }

  private def pullStream(stream: Stream[F, (Key, Value)], chunkSize: Int, lastOffset: Option[Key]): Pull[F, Value, Option[Key]] =
    stream.pull.unconsN(chunkSize, true).flatMap {

      case Some((chunk, next)) =>
        Pull.output(chunk.map(_._2)) >> pullStream(next, chunkSize, chunk.last.map(_._1).orElse(lastOffset))

      case None =>
        Pull.pure(lastOffset)
    }

  private def makeIterator: Resource[F, RocksIterator] = {
    val acquire: F[RocksIterator]         = Sync[F].delay(db.newIterator())
    val release: RocksIterator => F[Unit] = i => blocker.delay(i.close())
    Resource.make(acquire)(release)
  }
}
