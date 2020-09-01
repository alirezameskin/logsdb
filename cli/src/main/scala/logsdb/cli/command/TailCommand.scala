package logsdb.cli.command

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.Metadata
import logsdb.protos.{QueryFs2Grpc, QueryParams}

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
        .map(l => mkString(l, messageOnly = true))
        .through(fs2.io.stdoutLines[IO, String](blocker))
        .compile
        .drain
    }
}
