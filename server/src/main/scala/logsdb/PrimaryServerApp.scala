package logsdb

import cats.effect.{ContextShift, IO, Timer}
import io.odin.Logger
import logsdb.component.{GrpcServer, HttpServer}
import logsdb.settings.AppSettings
import logsdb.storage.RocksDB

object PrimaryServerApp {

  def run(settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO], L: Logger[IO]): IO[Unit] = {
    val primary = for {
      rocksDb <- RocksDB.open[IO](settings.storage)
      grpc    <- GrpcServer.buildPrimaryServer(rocksDb, settings.server.port)
      http    <- HttpServer.build(settings.http, rocksDb)
    } yield (grpc, http)

    primary.use {
      case (grpc, http) =>
        for {
          hf <- http.run.start
          gf <- grpc.run.start
          _  <- gf.join
          _  <- hf.join
        } yield ()
    }
  }

}
