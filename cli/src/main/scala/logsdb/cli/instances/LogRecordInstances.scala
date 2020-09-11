package logsdb.cli.instances

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime}
import java.util.TimeZone

import cats.Show
import com.google.protobuf.struct.Struct
import com.google.protobuf.timestamp.Timestamp
import io.circe.Decoder.Result
import io.circe.syntax._
import io.circe.{Decoder, Encoder, HCursor}
import logsdb.protos.LogRecord

trait LogRecordInstances {
  private val EOL = java.lang.System.lineSeparator()

  implicit def decodeLogRecord(implicit sd: Decoder[Struct]): Decoder[LogRecord] = new Decoder[LogRecord] {
    override def apply(c: HCursor): Result[LogRecord] =
      for {
        time       <- c.downField("time").as[Long]
        message    <- c.downField("message").as[String]
        attributes <- c.downField("attributes").as[Option[Struct]]
        seconds = time / 1000
        nanos   = (time % 1000).toInt
      } yield LogRecord(Some(Timestamp(seconds, nanos)), message, attributes = attributes)
  }

  implicit def wholeRecordShow(implicit JE: Encoder[Struct]): Show[LogRecord] = new Show[LogRecord] {
    override def show(record: LogRecord): String = {

      val time = record.timestamp
        .map { timestamp =>
          LocalDateTime
            .ofInstant(Instant.ofEpochSecond(timestamp.seconds), TimeZone.getDefault.toZoneId)
            .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)
        }
        .getOrElse("")

      val attributes = record.attributes.map(_.asJson.noSpaces).getOrElse("")

      s"${time} ${record.message} ${attributes}${EOL}"
    }
  }
}
