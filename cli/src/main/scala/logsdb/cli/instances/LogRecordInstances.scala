package logsdb.cli.instances

import com.google.protobuf.struct.Struct
import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}
import logsdb.protos.LogRecord

trait LogRecordInstances {

  implicit def decodeLogRecord(implicit sd: Decoder[Struct]): Decoder[LogRecord] = new Decoder[LogRecord] {
    override def apply(c: HCursor): Result[LogRecord] =
      for {
        time       <- c.downField("time").as[Long]
        message    <- c.downField("message").as[String]
        attributes <- c.downField("attributes").as[Option[Struct]]
      } yield LogRecord(time, message, attributes = attributes)
  }
}
