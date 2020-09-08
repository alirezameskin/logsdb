package logsdb

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import logsdb.component.{GrpcServer, Replicator}
import logsdb.settings.{AppSettings, ServerSettings}
import logsdb.storage.RocksDB

class ReplicaServerApp(R: RocksDB[IO], settings: AppSettings, primary: ServerSettings)(
  implicit CS: ContextShift[IO],
  T: Timer[IO]
) {

  def run: IO[Unit] = {
    val resources = for {
      grpc       <- GrpcServer.buildReplicaServer(R, settings.server.port)
      replicator <- Replicator.build[IO](R, settings, primary)
    } yield (grpc, replicator)

    resources.use {
      case (grpc, replicator) =>
        for {
          gf <- grpc.run.start
          rf <- replicator.run.start
          _  <- gf.join
          _  <- rf.join
        } yield ()
    }
  }
}

object ReplicaServerApp {
  def run(settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO]): IO[Unit] = {

    val replica = for {
      primary <- Resource.liftF(
        IO.fromOption(settings.replication.primary)(new RuntimeException("There is not any primary configuration"))
      )
      blocker <- Blocker[IO]
      rocksDb <- RocksDB.open[IO](settings.storage.path, blocker)
      server  <- Resource.pure[IO, ReplicaServerApp](new ReplicaServerApp(rocksDb, settings, primary))
    } yield server

    replica.use(_.run)
  }

}
