package logsdb.cli.command

import java.time.Instant

import cats.effect.{Blocker, ContextShift, IO}
import cats.implicits._
import com.google.protobuf.timestamp.Timestamp
import com.monovore.decline.Opts
import io.circe.parser.parse
import io.grpc._
import fs2._
import logsdb.cli.implicits._
import logsdb.protos._

case class PushOptions(host: String, port: Int, collection: String, isJson: Boolean = false, chunkSize: Int)

object PushCommand extends AbstractCommand {
  override type OPTIONS = PushOptions

  private val jsonOpts: Opts[Boolean] =
    Opts.flag("json", "Json Input.", short = "j").orFalse

  private val chunkSizeOpts: Opts[Int] =
    Opts.option[Int]("chunk-size", "Chunk size", short = "l").orElse(Opts(10))

  def options: Opts[PushOptions] = Opts.subcommand("push", "Push logs to server") {
    (hostOpts, portOpts, collectionOpts, jsonOpts, chunkSizeOpts).mapN(PushOptions)
  }

  def execute(options: PushOptions)(implicit CS: ContextShift[IO]): IO[Unit] =
    Blocker[IO].use { blocker =>
      val recordsConverter = toRecords(options.isJson)
      val requestConverter = toPushRequest(options.collection)
      val stream           = stdin(blocker, options.chunkSize).through(recordsConverter).through(requestConverter)

      val result = for {
        channel <- makeChannel(options.host, options.port)
        pusher = StorageFs2Grpc.stub[IO](channel, errorAdapter = ea)
        res <- pusher.push(stream, new Metadata())
      } yield res

      result.compile.drain
    }

  def toRecords(isJson: Boolean): Pipe[IO, List[String], List[LogRecord]] =
    in =>
      if (isJson) {
        in.evalMap(lines => lines.traverse(parseJson))
      } else {
        val now       = Instant.now()
        val timestamp = Timestamp(now.getEpochSecond, now.getNano)

        in.map(lines => lines.map(line => LogRecord(Some(timestamp), line)))
      }

  def parseJson(content: String): IO[LogRecord] =
    IO.fromEither(parse(content).flatMap(_.as[LogRecord]))

  def toPushRequest(collection: String): Pipe[IO, List[LogRecord], PushRequest] =
    in => in.map(records => PushRequest(collection, records))

  def stdin(blocker: Blocker, chunkSize: Int)(implicit CS: ContextShift[IO]): fs2.Stream[IO, List[String]] =
    fs2.io
      .stdin[IO](10, blocker)
      .through(text.utf8Decode)
      .through(text.lines)
      .filter(_.nonEmpty)
      .chunkN(chunkSize, allowFewer = true)
      .map(_.toList)
}
