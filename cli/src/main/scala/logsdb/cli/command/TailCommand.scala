package logsdb.cli.command

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.Metadata
import logsdb.cli.implicits._
import logsdb.protos.{LogRecord, QueryParams, StorageFs2Grpc}

case class TailOptions(host: String, port: Int, collection: String, query: String)

object TailCommand extends AbstractCommand {
  override type OPTIONS = TailOptions

  private val queryOpts: Opts[String] =
    Opts.argument[String]("Query").orElse(Opts(""))

  def options: Opts[TailOptions] = Opts.subcommand("tail", "Tail") {
    (hostOpts, portOpts, collectionOpts, queryOpts).mapN(TailOptions)
  }

  def execute(options: TailOptions)(implicit CS: ContextShift[IO]): IO[Unit] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        client = StorageFs2Grpc.stub[IO](channel, errorAdapter = ea)
        params = QueryParams(options.collection, 0L, 0, 10, query = options.query)
        logs <- client.tail(params, new Metadata())
      } yield logs

      result
        .through(fs2.io.stdoutLines[IO, LogRecord](blocker))
        .compile
        .drain
    }
}
