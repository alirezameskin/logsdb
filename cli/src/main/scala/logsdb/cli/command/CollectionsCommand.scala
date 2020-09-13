package logsdb.cli.command

import cats.effect.Console.io._
import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.Metadata
import logsdb.cli.implicits._
import logsdb.protos.{GetCollectionsRequest, StorageFs2Grpc}

case class CollectionsOptions(host: String, port: Int)

object CollectionsCommand extends AbstractCommand {
  override type OPTIONS = CollectionsOptions

  def options: Opts[CollectionsOptions] = Opts.subcommand("collections", "Get list of collections") {
    (hostOpts, portOpts).mapN(CollectionsOptions)
  }

  def execute(options: CollectionsOptions)(implicit CS: ContextShift[IO]): IO[Unit] =
    makeChannelResource(options.host, options.port).use { channel =>
      StorageFs2Grpc
        .stub[IO](channel, errorAdapter = ea)
        .collections(GetCollectionsRequest(), new Metadata())
        .flatMap(response => putStr(response))
    }

}
