package logsdb

import cats.effect.{ExitCode, IO, Resource}
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp
import io.grpc.{Server, ServerBuilder}
import logsdb.grpc.{PushService, QueryService}
import logsdb.storage.RocksDB
import org.lyranthe.fs2_grpc.java_runtime.syntax.ServerBuilderOps

object ServerApp extends CommandIOApp(name = "logsdb", header = "LogsDB server", version = "0.1") {
  override def main: Opts[IO[ExitCode]] = ServerOptions.opts.map { options =>
    val server = for {
      rocksDb <- RocksDB.open[IO](options.path)
      grpc    <- createGRPCServer(rocksDb, options.port)
    } yield grpc

    server.use(_ => IO.never).as(ExitCode.Success)
  }

  def createGRPCServer(R: RocksDB[IO], port: Int): Resource[IO, Server] = {
    val builder = ServerBuilder
      .forPort(port)
      .addService(PushService.build(R))
      .addService(QueryService.built(R))

    new ServerBuilderOps(builder)
      .resource[IO]
      .evalMap(server => IO(server.start()))
  }
}
