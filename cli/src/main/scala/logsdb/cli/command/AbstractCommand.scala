package logsdb.cli.command

import java.time.{Instant, LocalDateTime}
import java.time.format.DateTimeFormatter
import java.util.TimeZone

import cats.effect.IO
import com.monovore.decline.Opts
import fs2.Stream
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}
import logsdb.protos.LogRecord
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps

abstract class AbstractCommand extends Command {

  private[command] val ea: StatusRuntimeException => Option[Exception] = e =>
    Option(e.getCause).map(e => new RuntimeException(e.getMessage))

  private[command] val hostOpts: Opts[String] =
    Opts.option[String]("host", "Host", short = "h").orElse(Opts("127.0.0.1"))

  private[command] val portOpts: Opts[Int] =
    Opts.option[Int]("port", "Port", short = "p").orElse(Opts(9092))

  private[command] def makeChannel(host: String, port: Int): Stream[IO, ManagedChannel] = {
    val builder = ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext()

    new ManagedChannelBuilderOps(builder).stream[IO]
  }

  private[command] def mkString(record: LogRecord, messageOnly: Boolean): String = {
    val EOL = java.lang.System.lineSeparator()
    if (messageOnly) {
      s"${record.message}${EOL}"
    } else {
      val time = LocalDateTime
        .ofInstant(Instant.ofEpochMilli(record.time), TimeZone.getDefault.toZoneId)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

      s"${time} ${record.message}${EOL}"
    }
  }
}
