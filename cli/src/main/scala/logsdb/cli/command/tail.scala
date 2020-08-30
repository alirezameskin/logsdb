package logsdb.cli.command

import java.time.format.DateTimeFormatter
import java.time.{Instant, LocalDateTime}
import java.util.TimeZone

import cats.effect.{Blocker, ContextShift, ExitCode, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Metadata}
import logsdb.protos.{LogRecord, QueryFs2Grpc, QueryParams}
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps

case class TailOptions(host: String, port: Int)

object TailCommand {
  private val hostOpts: Opts[String] =
    Opts.option[String]("host", "Host", short = "h").orElse(Opts("127.0.0.1"))

  private val portOpts: Opts[Int] =
    Opts.option[Int]("port", "Port", short = "p").orElse(Opts(9092))

  def options: Opts[TailOptions] = Opts.subcommand("tail", "Tail") {
    (hostOpts, portOpts).mapN(TailOptions)
  }

  def execute(options: TailOptions)(implicit CS: ContextShift[IO]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        client = QueryFs2Grpc.stub[IO](channel)
        params = QueryParams(0L, 0, 10)
        logs <- client.tail(params, new Metadata())
      } yield logs

      result
        .map(l => mkString(l))
        .through(fs2.io.stdoutLines[IO, String](blocker))
        .compile
        .drain
        .as(ExitCode.Success)
    }

  private def makeChannel(host: String, port: Int): fs2.Stream[IO, ManagedChannel] = {
    val builder = ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext()

    new ManagedChannelBuilderOps(builder).stream[IO]
  }

  private def mkString(record: LogRecord, messageOnly: Boolean = true): String = {
    val EOL = java.lang.System.lineSeparator()
    if (messageOnly) {
      s"${record.message}${EOL}"
    } else {
      val time = LocalDateTime
        .ofInstant(Instant.ofEpochMilli(record.time), TimeZone.getDefault.toZoneId)
        .format(DateTimeFormatter.ISO_LOCAL_DATE_TIME)

      s"${record.id} ${time} ${record.message}${EOL}"
    }
  }
}
