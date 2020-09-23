package logsdb

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import io.odin.Logger
import logsdb.component.{GrpcServer, HttpServer, Replicator}
import logsdb.settings.AppSettings
import logsdb.storage.RocksDB

object ReplicaServerApp {
  def run(settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO], L: Logger[IO]): IO[Unit] = {

    val components = for {
      primary <- Resource.liftF(
        IO.fromOption(settings.replication.primary)(new RuntimeException("There is not any primary configuration"))
      )
      blocker    <- Blocker[IO]
      rocksDb    <- RocksDB.open[IO](settings.storage.path, blocker)
      grpc       <- GrpcServer.buildReplicaServer(rocksDb, settings.server.port)
      http       <- HttpServer.build(settings.http, rocksDb)
      replicator <- Replicator.build[IO](rocksDb, settings, primary)
    } yield (grpc, http, replicator)

    components.use {
      case (grpc, http, replicator) =>
        for {
          hf <- http.run.start
          gf <- grpc.run.start
          rf <- replicator.run.start
          _  <- hf.join
          _  <- gf.join
          _  <- rf.join
        } yield ()
    }
  }

}
