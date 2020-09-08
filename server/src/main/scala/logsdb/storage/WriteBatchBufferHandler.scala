package logsdb.storage

import com.google.protobuf.ByteString
import logsdb.protos.replication.Record
import org.rocksdb.WriteBatch

import scala.collection.mutable

final class WriteBatchBufferHandler(val buffer: mutable.Buffer[Record], columnFamilies: Map[Int, String])
    extends WriteBatch.Handler {

  override def put(columnFamilyId: Int, key: Array[Byte], value: Array[Byte]): Unit =
    buffer.append(
      Record(columnFamilies.getOrElse(columnFamilyId, "default"), ByteString.copyFrom(key), ByteString.copyFrom(value))
    )

  override def put(key: Array[Byte], value: Array[Byte]): Unit =
    buffer.append(Record("default", ByteString.copyFrom(key), ByteString.copyFrom(value)))

  override def merge(columnFamilyId: Int, key: Array[Byte], value: Array[Byte]): Unit = ()

  override def merge(key: Array[Byte], value: Array[Byte]): Unit = ()

  override def delete(columnFamilyId: Int, key: Array[Byte]): Unit = ()

  override def delete(key: Array[Byte]): Unit = ()

  override def singleDelete(columnFamilyId: Int, key: Array[Byte]): Unit = ()

  override def singleDelete(key: Array[Byte]): Unit = ()

  override def deleteRange(columnFamilyId: Int, beginKey: Array[Byte], endKey: Array[Byte]): Unit = ()

  override def deleteRange(beginKey: Array[Byte], endKey: Array[Byte]): Unit = ()

  override def logData(blob: Array[Byte]): Unit = ()

  override def putBlobIndex(columnFamilyId: Int, key: Array[Byte], value: Array[Byte]): Unit = ()

  override def markBeginPrepare(): Unit = ()

  override def markEndPrepare(xid: Array[Byte]): Unit = ()

  override def markNoop(emptyBatch: Boolean): Unit = ()

  override def markRollback(xid: Array[Byte]): Unit = ()

  override def markCommit(xid: Array[Byte]): Unit = ()
}
