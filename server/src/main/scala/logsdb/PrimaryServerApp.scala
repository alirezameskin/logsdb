package logsdb

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import io.grpc.{Server, ServerBuilder}
import logsdb.grpc.{ReplicatorService, StorageService}
import logsdb.settings.AppSettings
import logsdb.storage.RocksDB
import org.lyranthe.fs2_grpc.java_runtime.syntax.ServerBuilderOps

class PrimaryServerApp(R: RocksDB[IO], settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO]) {

  def run: IO[Unit] =
    createGRPCServer(R, settings.server.port).use { grpc =>
      IO(grpc.start())
    }

  private def createGRPCServer(R: RocksDB[IO], port: Int): Resource[IO, Server] = {
    val builder: ServerBuilder[_] =
      ServerBuilder
        .forPort(port)
        .addService(StorageService.built(R))
        .addService(ReplicatorService.built(R))

    new ServerBuilderOps(builder)
      .resource[IO]
  }
}

object PrimaryServerApp {

  def run(settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO]): IO[Unit] = {
    val primary = for {
      blocker <- Blocker[IO]
      rocksDb <- RocksDB.open[IO](settings.storage.path, blocker)
      server  <- Resource.pure[IO, PrimaryServerApp](new PrimaryServerApp(rocksDb, settings))
    } yield server

    primary.use(_.run)
  }

}
