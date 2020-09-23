package logsdb.instances

import io.circe
import io.circe.Json
import io.circe.syntax._
import logsdb.protos.{Collection, LogRecord, RecordId}
import logsdb.storage.Decoder.Result
import logsdb.storage.{Decoder, Encoder}

trait LogRecordInstances {

  implicit val recordEncoder: Encoder[LogRecord] = new Encoder[LogRecord] {
    override def encode(a: LogRecord): Either[Throwable, Array[Byte]] = Right(a.toByteArray)
  }

  implicit val recordDecoder: Decoder[LogRecord] = new Decoder[LogRecord] {
    override def decode(bytes: Array[Byte]): Result[LogRecord] = LogRecord.validate(bytes).toEither
  }

  implicit def recordJsonEncoder(implicit RIE: circe.Encoder[RecordId]): circe.Encoder[LogRecord] = new circe.Encoder[LogRecord] {
    override def apply(r: LogRecord): Json = Json.obj(
      ("id", r.id.asJson),
      ("message", Json.fromString(r.message))
    )
  }

}
