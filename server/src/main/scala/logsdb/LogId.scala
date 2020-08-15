package logsdb

import java.nio.ByteBuffer

import logsdb.storage.Decoder.Result
import logsdb.storage.{Decoder, Encoder}

case class LogId(time: Long, nuance: Option[Long] = None)

object LogId {

  implicit val encoder: Encoder[LogId] = new Encoder[LogId] {
    @inline override def encode(a: LogId): Either[Throwable, Array[Byte]] = {
      val buffer = ByteBuffer.allocate(16)
      buffer.putLong(a.time)
      buffer.putLong(a.nuance.getOrElse(0))

      Right(buffer.array())
    }
  }

  implicit val decoder: Decoder[LogId] = new Decoder[LogId] {
    @inline override def decode(bytes: Array[Byte]): Result[LogId] = {
      val buffer = ByteBuffer.wrap(bytes)
      buffer.flip()
      Right(LogId(buffer.getLong, Some(buffer.getLong)))
    }
  }

}
