package logsdb.storage

import cats.implicits._
import cats.{Applicative, MonadError}
import org.{rocksdb => jrocks}

class RocksDBImpl[F[_]](db: jrocks.RocksDB)(implicit M:MonadError[F, Throwable]) extends RocksDB[F]{

  def get(key: Array[Byte]): F[Option[Array[Byte]]] = Applicative[F].pure(Option(db.get(key)))

  def put(key: Array[Byte], value: Array[Byte]) : F[Unit] = db.put(key, value).pure[F]

  private def encodeValue[V](value: Option[Array[Byte]])(implicit D:Decoder[V]): Option[V] = value match {
    case Some(v) => D.decode(v).toOption
    case None => None
  }

  def get[K, V](key:K)(implicit K:Encoder[K], V:Decoder[V]): F[Option[V]] =
    for {
      bytes <- M.fromEither(K.encode(key))
      res <- this.get(bytes).map(b => encodeValue[V](b))
    } yield res

  def put[K, V](key: K, value: V)(implicit K: Encoder[K], V: Encoder[V]): F[Unit] =
    for {
      key <- M.fromEither(K.encode(key))
      value <- M.fromEither(V.encode(value))
      _ <- this.put(key, value)
    }  yield ()

  def startsWith[K, V](prefix: K)(implicit K: Encoder[K], V: Decoder[V]): F[Iterator[V]] = {
    for {
      bytes <- M.fromEither(K.encode(prefix))
      itr <- db.newIterator().pure[F]
      _ = itr.seek(bytes)
    } yield new Iterator[V] {
      override def hasNext: Boolean = itr.isValid

      override def next(): V = {
        V.decode(itr.value()).toOption.get
      }
    }
  }

  override def endsWith[K, V](key: K)(implicit K: Encoder[K], V: Decoder[V]): F[Iterator[V]] =
    for {
      bytes <- M.fromEither(K.encode(key))
      itr <- db.newIterator().pure[F]
      _ = itr.seekForPrev(bytes)
    } yield new Iterator[V] {
      override def hasNext: Boolean = itr.isValid

      override def next(): V = {
        val value = V.decode(itr.value()).toOption.get
        itr.prev()
        value
      }
    }
}
