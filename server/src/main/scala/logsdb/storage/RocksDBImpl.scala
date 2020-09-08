package logsdb.storage

import java.util.concurrent.TimeUnit

import cats.MonadError
import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{Blocker, ContextShift, Resource, Sync, Timer}
import cats.implicits._
import fs2.{Pull, RaiseThrowable, Stream}
import logsdb.implicits._
import logsdb.protos.replication.TransactionLog
import org.rocksdb.{ColumnFamilyDescriptor, ColumnFamilyHandle, RocksIterator}
import org.{rocksdb => jrocks}

import scala.concurrent.duration.FiniteDuration

class RocksDBImpl[F[_]: Sync: ContextShift: Timer: RaiseThrowable](
  db: jrocks.RocksDB,
  columnFamilies: Ref[F, Map[String, ColumnFamilyHandle]],
  blocker: Blocker,
  semaphore: Semaphore[F]
)(
  implicit M: MonadError[F, Throwable]
) extends RocksDB[F] {

  type Key   = Array[Byte]
  type Value = Array[Byte]

  def get(collection: String, key: Array[Byte]): F[Option[Array[Byte]]] =
    for {
      handle <- getColumnFamilyHandle(collection)
      record <- Option(db.get(handle, key)).pure[F]
    } yield record

  def put(collection: String, key: Array[Byte], value: Array[Byte]): F[Unit] =
    for {
      handle <- getColumnFamilyHandle(collection)
      _      <- db.put(handle, key, value).pure[F]
    } yield ()

  def get[K, V](collection: String, key: K)(implicit K: Encoder[K], V: Decoder[V]): F[Option[V]] =
    for {
      bytes <- M.fromEither(K.encode(key))
      res   <- this.get(collection, bytes).map(b => encodeValue[V](b))
    } yield res

  def put[K, V](collection: String, key: K, value: V)(implicit KE: Encoder[K], VE: Encoder[V]): F[Unit] =
    for {
      key   <- M.fromEither(KE.encode(key))
      value <- M.fromEither(VE.encode(value))
      _     <- this.put(collection, key, value)
    } yield ()

  def startsWith[K, V](collection: String, prefix: K)(implicit KE: Encoder[K], KD: Decoder[K], VD: Decoder[V]): Stream[F, V] = {
    val stream = for {
      iterator <- Stream.resource(makeIterator(collection))
      bytes    <- Stream.fromEither(KE.encode(prefix))
      _ = iterator.seek(bytes)
      stream <- iterator.valuesStream[F]
    } yield stream

    stream.map(v => VD.decode(v)).map(_.toOption).filter(_.isDefined).map(_.get)
  }

  override def transactionsSince(sequenceNumber: Long): Stream[F, TransactionLog] = {
    val iterator = Resource.make(Sync[F].delay(db.getUpdatesSince(sequenceNumber)))(i => blocker.delay(i.close()))

    for {
      families <- Stream.eval(getColumnFamilies())
      iterator <- Stream.resource(iterator)
      str      <- iterator.stream[F](families).filter(_.sequenceNumber > sequenceNumber)
    } yield str
  }

  override def latestSequenceNumber: F[Long] =
    db.getLatestSequenceNumber.pure[F]

  override def tail(collection: String, from: Option[Array[Byte]]): Stream[F, Array[Byte]] =
    tailPull(collection, 100, FiniteDuration(500, TimeUnit.MILLISECONDS), from).void.stream

  def tailPull(
    collection: String,
    chunkSize: Int,
    pullDelay: FiniteDuration,
    lastOffset: Option[Key]
  ): Pull[F, Value, Option[Key]] = {
    val stream = lastOffset match {
      case Some(offset) =>
        for {
          iterator <- Stream.resource(makeIterator(collection))
          _ = iterator.seek(offset)
          _ = iterator.next()
          str <- iterator.stream[F]
        } yield str
      case None =>
        for {
          iterator <- Stream.resource(makeIterator(collection))
          _ = iterator.seekToLast()
          str <- iterator.stream[F]
        } yield str
    }

    pullStream(stream, chunkSize, lastOffset).flatMap { offset =>
      Pull.eval(Timer[F].sleep(pullDelay)) >> tailPull(collection, chunkSize, pullDelay, offset)
    }
  }

  private def pullStream(stream: Stream[F, (Key, Value)], chunkSize: Int, lastOffset: Option[Key]): Pull[F, Value, Option[Key]] =
    stream.pull.unconsN(chunkSize, true).flatMap {

      case Some((chunk, next)) =>
        Pull.output(chunk.map(_._2)) >> pullStream(next, chunkSize, chunk.last.map(_._1).orElse(lastOffset))

      case None =>
        Pull.pure(lastOffset)
    }

  private def makeIterator(columnFamily: String): Resource[F, RocksIterator] =
    for {
      handle   <- Resource.liftF(getColumnFamilyHandle(columnFamily))
      iterator <- Resource.make(Sync[F].delay(db.newIterator(handle)))(i => blocker.delay(i.close()))
    } yield iterator

  private def getColumnFamilies(): F[Map[Int, String]] =
    for {
      families <- columnFamilies.get
      list     <- families.map(r => (r._2.getID, r._1)).pure[F]
    } yield list

  private def getColumnFamilyHandle(name: String): F[ColumnFamilyHandle] =
    for {
      families <- columnFamilies.get
      family   <- families.get(name).pure[F]
      handle <- family match {
        case Some(handle) => handle.pure[F]
        case None         => createColumnFamily(name)
      }

    } yield handle

  private def createColumnFamily(name: String): F[ColumnFamilyHandle] =
    for {
      _        <- semaphore.acquire
      families <- columnFamilies.get
      changes <- families.get(name) match {
        case Some(_) => families.pure[F]
        case None    => (families + (name -> db.createColumnFamily(new ColumnFamilyDescriptor(name.getBytes)))).pure[F]
      }
      families <- columnFamilies.updateAndGet(_ => changes)
      handle   <- families(name).pure[F]

    } yield handle

  private def encodeValue[V](value: Option[Array[Byte]])(implicit D: Decoder[V]): Option[V] = value match {
    case Some(v) => D.decode(v).toOption
    case None    => None
  }

}
