package logsdb.instances

import logsdb.protos.LogRecord
import logsdb.storage.Decoder.Result
import logsdb.storage.{Decoder, Encoder}

trait LogRecordInstances {

  implicit val recordEncoder: Encoder[LogRecord] = new Encoder[LogRecord] {
    override def encode(a: LogRecord): Either[Throwable, Array[Byte]] = Right(a.toByteArray)
  }

  implicit val recordDecoder: Decoder[LogRecord] = new Decoder[LogRecord] {
    override def decode(bytes: Array[Byte]): Result[LogRecord] = LogRecord.validate(bytes).toEither
  }
}