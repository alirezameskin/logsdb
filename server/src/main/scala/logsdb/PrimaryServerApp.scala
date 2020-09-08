package logsdb

import cats.effect.{Blocker, ContextShift, IO, Timer}
import logsdb.component.GrpcServer
import logsdb.settings.AppSettings
import logsdb.storage.RocksDB

object PrimaryServerApp {

  def run(settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO]): IO[Unit] = {
    val primary = for {
      blocker <- Blocker[IO]
      rocksDb <- RocksDB.open[IO](settings.storage.path, blocker)
      server  <- GrpcServer.buildPrimaryServer(rocksDb, settings.server.port)
    } yield server

    primary.use(s => s.run)
  }

}
