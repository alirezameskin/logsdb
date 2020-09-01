package logsdb.syntax

import cats.effect.Sync
import fs2.Stream
import org.rocksdb.RocksIterator

trait RocksIteratorSyntax {
  implicit final def rocksIteratorSyntax(iterator: RocksIterator): RocksIteratorOps =
    new RocksIteratorOps(iterator)
}

final class RocksIteratorOps(val rocksIterator: RocksIterator) extends AnyVal {

  def stream[F[_]: Sync]: Stream[F, (Array[Byte], Array[Byte])] =
    Stream.fromIterator {
      new Iterator[(Array[Byte], Array[Byte])] {
        override def hasNext: Boolean = rocksIterator.isValid

        override def next(): (Array[Byte], Array[Byte]) = {
          val key   = rocksIterator.key()
          val value = rocksIterator.value()

          rocksIterator.next()

          (key, value)
        }
      }
    }

  def valuesStream[F[_]: Sync]: Stream[F, Array[Byte]] =
    Stream.fromIterator {
      new Iterator[Array[Byte]] {
        override def hasNext: Boolean = rocksIterator.isValid

        override def next(): Array[Byte] = {
          val value = rocksIterator.value()
          rocksIterator.next()

          value
        }
      }
    }
}
