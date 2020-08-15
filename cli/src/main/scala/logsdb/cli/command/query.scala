package logsdb.cli.command

import java.time.Instant
import java.time.format.DateTimeFormatter

import cats.effect.{Blocker, ContextShift, ExitCode, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.{ManagedChannel, ManagedChannelBuilder, Metadata}
import logsdb.protos.{QueryFs2Grpc, QueryParams}
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps

case class QueryOptions(host: String, port: Int, limit: Int, from: Option[Long], to: Long)

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

  def options: Opts[QueryOptions] = Opts.subcommand("query", "Query") {
    (hostOpts, portOpts, limitOpts, fromOpts, toOpts).mapN(QueryOptions)
  }

  def execute(options: QueryOptions)(implicit CS: ContextShift[IO]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        client = QueryFs2Grpc.stub[IO](channel)
        params = QueryParams(options.from.getOrElse(0L), options.to, options.limit)
        logs <- client.query(params, new Metadata())
      } yield logs

      result.map(_.message).through(fs2.io.stdoutLines[IO, String](blocker)).compile.drain.as(ExitCode.Success)
    }

  private def makeChannel(host: String, port: Int): fs2.Stream[IO, ManagedChannel] = {
    val builder = ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext()

    new ManagedChannelBuilderOps(builder).stream[IO]
  }
}
