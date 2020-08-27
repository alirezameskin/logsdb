import java.nio.ByteBuffer

import logsdb.protos.{LogRecord, RecordId}
import logsdb.storage.Decoder.Result
import logsdb.storage.{Decoder, Encoder}

package object logsdb {
  implicit val recordEncoder: Encoder[LogRecord] = new Encoder[LogRecord] {
    override def encode(a: LogRecord): Either[Throwable, Array[Byte]] = Right(a.toByteArray)
  }

  implicit val recordDecoder: Decoder[LogRecord] = new Decoder[LogRecord] {
    override def decode(bytes: Array[Byte]): Result[LogRecord] = LogRecord.validate(bytes).toEither
  }

  implicit val recordIdEncoder: Encoder[RecordId] = new Encoder[RecordId] {
    override def encode(a: RecordId): Either[Throwable, Array[Byte]] = {
      val buffer = ByteBuffer.allocate(16)
      buffer.putLong(a.time)
      buffer.putLong(a.nuance)

      Right(buffer.array())
    }
  }

  implicit val recordIDecoder: Decoder[RecordId] = new Decoder[RecordId] {
    override def decode(bytes: Array[Byte]): Result[RecordId] = {
      val buffer = ByteBuffer.allocate(16)
      buffer.put(bytes)
      buffer.flip()

      Right(RecordId(buffer.getLong, buffer.getLong))
    }
  }
}
