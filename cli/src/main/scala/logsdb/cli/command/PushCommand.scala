package logsdb.cli.command

import java.time.Instant

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.circe.parser.parse
import io.grpc._
import fs2._
import logsdb.cli.implicits._
import logsdb.protos._

case class PushOptions(host: String, port: Int, collection: String, isJson: Boolean = false)

object PushCommand extends AbstractCommand {
  override type OPTIONS = PushOptions

  private val jsonOpts: Opts[Boolean] =
    Opts.flag("json", "Json Input.", short = "j").orFalse

  def options: Opts[PushOptions] = Opts.subcommand("push", "Push logs to server") {
    (hostOpts, portOpts, collectionOpts, jsonOpts).mapN(PushOptions)
  }

  def execute(options: PushOptions)(implicit CS: ContextShift[IO]): IO[Unit] =
    Blocker[IO].use { blocker =>
      val recordConverter  = toRecord(options.isJson)
      val requestConverter = toPushRequest(options.collection)
      val stream           = stdin(blocker).through(recordConverter).through(requestConverter)

      val result = for {
        channel <- makeChannel(options.host, options.port)
        pusher = StorageFs2Grpc.stub[IO](channel, errorAdapter = ea)
        res <- pusher.push(stream, new Metadata())
      } yield res

      result.compile.drain
    }

  def toRecord(isJson: Boolean): Pipe[IO, String, LogRecord] =
    in =>
      if (isJson) {
        in.evalMap(line => parseJson(line))
      } else {
        in.map(line => LogRecord(Instant.now().toEpochMilli, line))
      }

  def parseJson(content: String): IO[LogRecord] =
    IO.fromEither(parse(content).flatMap(_.as[LogRecord]))

  def toPushRequest(collection: String): Pipe[IO, LogRecord, PushRequest] =
    in => in.map(record => PushRequest(collection, Some(record)))

  def stdin(blocker: Blocker)(implicit CS: ContextShift[IO]): fs2.Stream[IO, String] =
    fs2.io
      .stdin[IO](10, blocker)
      .through(text.utf8Decode)
      .through(text.lines)
      .filter(_.nonEmpty)
}
