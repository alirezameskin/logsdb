package logsdb.cli.command

import java.time.Instant

import cats.effect.{Blocker, ContextShift, ExitCode, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc._
import fs2._
import logsdb.protos._
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps

case class PushOptions(host: String, port: Int, text: Boolean = true)

object PushCommand {
  private val hostOpts: Opts[String] =
    Opts.option[String]("host", "Host", short = "h").orElse(Opts("127.0.0.1"))

  private val portOpts: Opts[Int] =
    Opts.option[Int]("port", "Port", short = "p").orElse(Opts(9092))

  private val textOpts: Opts[Boolean] =
    Opts.flag("text", "Text Input.", short = "t").orTrue

  def options: Opts[PushOptions] = Opts.subcommand("push", "Push logs to server") {
    (hostOpts, portOpts, textOpts).mapN(PushOptions)
  }

  def execute(options: PushOptions)(implicit CS: ContextShift[IO]): IO[ExitCode] =
    Blocker[IO].use { blocker =>
      val result = for {
        channel <- makeChannel(options.host, options.port)
        pusher = PusherFs2Grpc.stub[IO](channel)
        rows   = stdin(blocker).map(s => LogRecord(Instant.now().toEpochMilli, s))
        res <- pusher.push(rows, new Metadata())
      } yield res

      result.compile.drain.as(ExitCode.Success)
    }

  private def makeChannel(host: String, port: Int): Stream[IO, ManagedChannel] = {
    val builder = ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext()

    new ManagedChannelBuilderOps(builder).stream[IO]
  }

  private def stdin(blocker: Blocker)(implicit CS: ContextShift[IO]): fs2.Stream[IO, String] =
    fs2.io
      .stdin[IO](10, blocker)
      .through(text.utf8Decode)
      .through(text.lines)
      .filter(_.nonEmpty)
}
