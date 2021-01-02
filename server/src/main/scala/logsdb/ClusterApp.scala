package logsdb

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import io.odin.Logger
import logsdb.settings.AppSettings
import logsdb.storage.{RocksDB, StateMachine}
import raft4s.effect.storage.file.{FileSnapshotStorage, FileStateStorage}
import raft4s.effect.storage.rocksdb.RocksDBLogStorage
import raft4s.effect.{odinLogger, RaftCluster}
import raft4s.storage.serialization.default._
import raft4s.{internal, Cluster, Configuration, Storage}

import java.nio.file.{Files, Path, Paths}
import scala.util.Try

object ClusterApp {

  def run(settings: AppSettings)(implicit CS: ContextShift[IO], T: Timer[IO], L: Logger[IO]): IO[Unit] = {
    val primary = for {
      blocker <- Blocker[IO]
      rocksDb <- RocksDB.open[IO](settings.storage)
      cluster <- makeCluster(settings, rocksDb)
      grpc    <- GrpcServer.build(cluster, settings.server.port)
    } yield (cluster, grpc)

    primary.use {
      case (cluster, grpc) =>
        for {
          _ <- if (settings.cluster.seed.isEmpty) cluster.start
          else cluster.join(raft4s.Node(settings.cluster.seed.get.host, settings.cluster.seed.get.port))
          _ <- grpc.run
        } yield ()
    }
  }

  private def makeCluster(
    settings: AppSettings,
    rocksDB: RocksDB[IO]
  )(implicit T: Timer[IO], CS: ContextShift[IO], L: Logger[IO]): Resource[IO, Cluster[IO]] = {

    implicit val logger: internal.Logger[IO] = odinLogger(L)

    import raft4s.effect.rpc.grpc.io.implicits._

    val config = Configuration(
      local = raft4s.Node(settings.cluster.host, settings.cluster.port),
      members = Seq.empty,
      followerAcceptRead = true,
      logCompactionThreshold = 10,
      electionMinDelayMillis = 0,
      electionMaxDelayMillis = 2000,
      heartbeatIntervalMillis = 2000,
      heartbeatTimeoutMillis = 10000
    )

    for {
      storage      <- makeStorage(Paths.get(settings.cluster.path))
      stateMachine <- Resource.liftF(StateMachine.build(rocksDB))
      cluster      <- RaftCluster.resource[IO](config, storage, stateMachine)
    } yield cluster
  }

  private def makeStorage(path: Path)(implicit L: raft4s.internal.Logger[IO]): Resource[IO, Storage[IO]] =
    for {
      _               <- Resource.liftF(createDirectory(path))
      _               <- Resource.liftF(createDirectory(path.resolve("snapshots")))
      logStorage      <- RocksDBLogStorage.open[IO](path.resolve("logs"))
      stateStorage    <- Resource.pure[IO, FileStateStorage[IO]](FileStateStorage.open[IO](path.resolve("state")))
      snapshotStorage <- Resource.liftF(FileSnapshotStorage.open[IO](path.resolve("snapshots")))
    } yield Storage(logStorage, stateStorage, snapshotStorage)

  private def createDirectory(path: Path): IO[Unit] = IO.fromEither {
    Try(Files.createDirectory(path)).toEither match {
      case Right(_)                           => Right(())
      case Left(_) if Files.isDirectory(path) => Right(())
      case Left(error)                        => Left(error)
    }
  }

}
