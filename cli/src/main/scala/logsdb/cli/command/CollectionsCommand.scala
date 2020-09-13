package logsdb.cli.command

import cats.effect.{ContextShift, IO}
import cats.implicits._
import com.monovore.decline.Opts
import io.grpc.Metadata
import logsdb.cli.utils.Tabulator
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
        .map { response =>
          val header = List(" Collection Id ", " Collection Name ", " Collection Size (Bytes) ", " Collection Size ")
          val rows = response.collections.map { c =>
            List(c.id.toString, c.name, c.size.toString, humanReadableSize(c.size))
          }

          println(Tabulator.format(List(header).appendedAll(rows)))
        }
    }

  private def humanReadableSize(bytes: Long): String = {
    val (baseValue, unitStrings) = (1024, Vector("B", "KiB", "MiB", "GiB", "TiB", "PiB", "EiB", "ZiB", "YiB"))

    def getExponent(curBytes: Long, baseValue: Int, curExponent: Int = 0): Int =
      if (curBytes < baseValue) curExponent
      else {
        val newExponent = 1 + curExponent
        getExponent(curBytes / (baseValue * newExponent), baseValue, newExponent)
      }

    val exponent   = getExponent(bytes, baseValue)
    val divisor    = Math.pow(baseValue, exponent)
    val unitString = unitStrings(exponent)

    f"${bytes / divisor}%.1f $unitString"
  }
}
