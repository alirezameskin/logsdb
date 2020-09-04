package logsdb.cli.command

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.Metadata
import logsdb.protos.{LogRecord, QueryFs2Grpc, QueryParams}
import logsdb.cli.implicits._

case class TailOptions(host: String, port: Int, collection: String)

object TailCommand extends AbstractCommand {
  override type OPTIONS = TailOptions

  def options: Opts[TailOptions] = Opts.subcommand("tail", "Tail") {
    (hostOpts, portOpts, collectionOpts).mapN(TailOptions)
  }

  def execute(options: TailOptions)(implicit CS: ContextShift[IO]): IO[Unit] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        client = QueryFs2Grpc.stub[IO](channel, errorAdapter = ea)
        params = QueryParams(options.collection, 0L, 0, 10)
        logs <- client.tail(params, new Metadata())
      } yield logs

      result
        .through(fs2.io.stdoutLines[IO, LogRecord](blocker))
        .compile
        .drain
    }
}
