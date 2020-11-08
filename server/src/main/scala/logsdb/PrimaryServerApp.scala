package logsdb

import cats.effect.{Blocker, ContextShift, IO, Timer}
import io.odin.Logger
import logsdb.component.{Cluster, GrpcServer, HttpServer}
import logsdb.settings.AppSettings
import logsdb.storage.RocksDB

object PrimaryServerApp {

  def run(settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO], L: Logger[IO]): IO[Unit] = {
    val primary = for {
      blocker <- Blocker[IO]
      rocksDb <- RocksDB.open[IO](settings.storage)
      grpc    <- GrpcServer.buildPrimaryServer(rocksDb, settings.server.port)
      http    <- HttpServer.build(settings.http, rocksDb, blocker)
      cluster <- Cluster.build(settings.cluster)
    } yield (grpc, http, cluster)

    primary.use {
      case (grpc, http, cluster) =>
        for {
          hf <- http.run.start
          gf <- grpc.run.start
          cf <- cluster.run.start
          _  <- gf.join
          _  <- hf.join
          _  <- cf.join
        } yield ()
    }
  }

}
