package logsdb.cli.instances

import io.circe.Decoder.Result
import io.circe.{Decoder, HCursor}
import logsdb.protos.LogRecord

trait LogRecordInstances {

  implicit val decodeLogRecord = new Decoder[LogRecord] {
    override def apply(c: HCursor): Result[LogRecord] =
      for {
        time    <- c.downField("time").as[Long]
        message <- c.downField("message").as[String]
      } yield LogRecord(time, message)
  }
}
