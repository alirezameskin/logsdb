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

case class QueryOptions(host: String, port: Int, limit: Int, from: Option[Long], to: Long, messageOnly: Boolean = false)

object QueryCommand {
  private val hostOpts: Opts[String] =
    Opts.option[String]("host", "Host", short = "h").orElse(Opts("127.0.0.1"))

  private val portOpts: Opts[Int] =
    Opts.option[Int]("port", "Port", short = "p").orElse(Opts(9092))

  private val limitOpts: Opts[Int] =
    Opts.option[Int]("limit", "Limit", short = "l").orElse(Opts(100))

  private val fromOpts: Opts[Option[Long]] =
    Opts
      .option[String]("from", "From Date", short = "f")
      .map(d => Instant.from(DateTimeFormatter.ISO_INSTANT.parse(d)).toEpochMilli)
      .orNone

  private val toOpts: Opts[Long] =
    Opts
      .option[String]("to", "To Date", short = "t")
      .map(d => Instant.from(DateTimeFormatter.ISO_INSTANT.parse(d)).toEpochMilli)
      .orElse(Opts(Instant.now().toEpochMilli))

  private val msgOnlyOpts: Opts[Boolean] =
    Opts.flag("message-only", "Message only output.", short = "m").orFalse

  def options: Opts[QueryOptions] = Opts.subcommand("query", "Query") {
    (hostOpts, portOpts, limitOpts, fromOpts, toOpts, msgOnlyOpts).mapN(QueryOptions)
  }

  def execute(options: QueryOptions)(implicit CS: ContextShift[IO]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        client = QueryFs2Grpc.stub[IO](channel)
        params = QueryParams(options.from.getOrElse(0L), options.to, options.limit)
        logs <- client.query(params, new Metadata())
      } yield logs

      result
        .map(l => mkString(l, options.messageOnly))
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

  private def mkString(record: LogRecord, messageOnly: Boolean): String = {
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
