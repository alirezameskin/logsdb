package logsdb.cli.command

import java.time.Instant
import java.time.format.DateTimeFormatter

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.Metadata
import logsdb.cli.implicits._
import logsdb.protos.{LogRecord, QueryParams, StorageFs2Grpc}

case class QueryOptions(
  host: String,
  port: Int,
  collection: String,
  limit: Int,
  from: Option[Long],
  to: Long,
  query: String
)

object QueryCommand extends AbstractCommand {
  override type OPTIONS = QueryOptions

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

  private val queryOpts: Opts[String] =
    Opts.argument[String]("Query").orElse(Opts(""))

  def options: Opts[QueryOptions] = Opts.subcommand("query", "Query") {
    (hostOpts, portOpts, collectionOpts, limitOpts, fromOpts, toOpts, queryOpts).mapN(QueryOptions)
  }

  def execute(options: QueryOptions)(implicit CS: ContextShift[IO]): IO[Unit] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        params = QueryParams(options.collection, options.from.getOrElse(0L), options.to, options.limit, query = options.query)
        client = StorageFs2Grpc.stub[IO](channel, errorAdapter = ea)
        logs <- client.query(params, new Metadata())
      } yield logs

      result
        .through(fs2.io.stdoutLines[IO, LogRecord](blocker))
        .compile
        .drain
    }

}
