package logsdb.syntax

import cats.effect.Sync
import fs2.Stream
import logsdb.protos.replication._
import logsdb.storage.WriteBatchBufferHandler
import org.rocksdb.TransactionLogIterator

trait TransactionLogIteratorSyntax {

  implicit final def transactionLogIteratorSyntax(iterator: TransactionLogIterator): TransactionLogIteratorOps =
    new TransactionLogIteratorOps(iterator)
}

final class TransactionLogIteratorOps(val logIterator: TransactionLogIterator) extends AnyVal {

  def stream[F[_]: Sync](columnFamilyNames: Map[Int, String]): Stream[F, TransactionLog] =
    Stream.fromIterator {
      new Iterator[TransactionLog] {
        override def hasNext: Boolean = logIterator.isValid

        override def next(): TransactionLog = {

          val buffer                  = scala.collection.mutable.ListBuffer.empty[Record]
          val writeBatch              = logIterator.getBatch.writeBatch()
          val writeBatchBufferHandler = new WriteBatchBufferHandler(buffer, columnFamilyNames)
          val sequenceNumber          = logIterator.getBatch.sequenceNumber()

          writeBatch.iterate(writeBatchBufferHandler);

          logIterator.next()

          TransactionLog(sequenceNumber, buffer.toSeq)
        }
      }
    }
}
