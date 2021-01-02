package logsdb.storage

import cats.MonadError
import cats.effect.concurrent.{Ref, Semaphore}
import cats.effect.{ContextShift, Resource, Sync, Timer}
import cats.implicits._
import fs2.{RaiseThrowable, Stream}
import logsdb.implicits._
import logsdb.protos.Collection
import org.rocksdb.{ColumnFamilyDescriptor, ColumnFamilyHandle, RocksIterator}
import org.{rocksdb => jrocks}

class RocksDBImpl[F[_]: Sync: ContextShift: Timer: RaiseThrowable](
  db: jrocks.RocksDB,
  columnFamilies: Ref[F, Map[String, ColumnFamilyHandle]],
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

  override def startsWith(collection: String, prefix: Array[Byte]): Stream[F, (Array[Byte], Array[Byte])] =
    for {
      iterator <- Stream.resource(makeIterator(collection))
      _ = iterator.seek(prefix)
      stream <- iterator.stream[F]
    } yield stream

  override def collections: F[Seq[Collection]] =
    for {
      families <- columnFamilies.get
      collections = families.map(f => Collection(f._2.getID, f._1, db.getColumnFamilyMetaData(f._2).size))
    } yield collections.toSeq.sortBy(_.id)

  private def makeIterator(columnFamily: String): Resource[F, RocksIterator] =
    for {
      handle   <- Resource.liftF(getColumnFamilyHandle(columnFamily))
      iterator <- Resource.make(Sync[F].delay(db.newIterator(handle)))(i => Sync[F].delay(i.close()))
    } yield iterator

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
      _        <- semaphore.release

    } yield handle

}
