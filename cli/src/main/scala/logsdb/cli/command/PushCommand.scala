package logsdb.cli.command

import java.time.Instant

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc._
import fs2._
import logsdb.protos._

case class PushOptions(host: String, port: Int, text: Boolean = true)

object PushCommand extends AbstractCommand {
  override type OPTIONS = PushOptions

  private val textOpts: Opts[Boolean] =
    Opts.flag("text", "Text Input.", short = "t").orTrue

  def options: Opts[PushOptions] = Opts.subcommand("push", "Push logs to server") {
    (hostOpts, portOpts, textOpts).mapN(PushOptions)
  }

  def execute(options: PushOptions)(implicit CS: ContextShift[IO]): IO[Unit] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        pusher = PusherFs2Grpc.stub[IO](channel, errorAdapter = ea)
        rows   = stdin(blocker).map(s => LogRecord(Instant.now().toEpochMilli, s))
        res <- pusher.push(rows, new Metadata())
      } yield res

      result.compile.drain
    }

  private def stdin(blocker: Blocker)(implicit CS: ContextShift[IO]): fs2.Stream[IO, String] =
    fs2.io
      .stdin[IO](10, blocker)
      .through(text.utf8Decode)
      .through(text.lines)
      .filter(_.nonEmpty)
}
