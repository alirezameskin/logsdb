package logsdb.cli.command

import cats.effect.IO
import com.monovore.decline.Opts
import fs2.Stream
import io.grpc.{ManagedChannel, ManagedChannelBuilder, StatusRuntimeException}
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps

abstract class AbstractCommand extends Command {

  private[command] val ea: StatusRuntimeException => Option[Exception] = e =>
    Option(e.getCause).map(e => new RuntimeException(e.getMessage))

  private[command] val hostOpts: Opts[String] =
    Opts.option[String]("host", "Host", short = "h").orElse(Opts("127.0.0.1"))

  private[command] val portOpts: Opts[Int] =
    Opts.option[Int]("port", "Port", short = "p").orElse(Opts(9092))

  private[command] val collectionOpts: Opts[String] =
    Opts.option[String]("collection", "Collection name", short = "c").orElse(Opts("default"))

  private[command] def makeChannel(host: String, port: Int): Stream[IO, ManagedChannel] = {
    val builder: ManagedChannelBuilder[_] = ManagedChannelBuilder
      .forAddress(host, port)
      .usePlaintext()

    new ManagedChannelBuilderOps(builder).stream[IO]
  }
}
