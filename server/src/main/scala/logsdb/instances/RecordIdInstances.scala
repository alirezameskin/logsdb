package logsdb.instances

import java.nio.ByteBuffer

import io.circe
import io.circe.Json
import logsdb.protos.RecordId
import logsdb.storage.Decoder.Result
import logsdb.storage.{Decoder, Encoder}

trait RecordIdInstances {

  implicit val recordIdEncoder: Encoder[RecordId] = new Encoder[RecordId] {
    override def encode(a: RecordId): Either[Throwable, Array[Byte]] = {
      val buffer = ByteBuffer.allocate(16)
      buffer.putLong(a.seconds)
      buffer.putInt(a.nanos)
      buffer.putInt(a.nuance)

      Right(buffer.array())
    }
  }

  implicit val recordIDecoder: Decoder[RecordId] = new Decoder[RecordId] {
    override def decode(bytes: Array[Byte]): Result[RecordId] = {
      val buffer = ByteBuffer.allocate(16)
      buffer.put(bytes)
      buffer.flip()

      Right(RecordId(buffer.getLong, buffer.getInt, buffer.getInt))
    }
  }

  implicit val recordIdJsonEncoder: circe.Encoder[RecordId] = new circe.Encoder[RecordId] {
    override def apply(a: RecordId): Json = Json.fromString(
      s"${a.seconds}.${a.nanos}.${a.nuance}"
    )
  }
}
