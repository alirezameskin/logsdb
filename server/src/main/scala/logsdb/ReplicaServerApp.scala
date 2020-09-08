package logsdb

import java.util.concurrent.TimeUnit

import cats.effect.{Blocker, ContextShift, IO, Resource, Timer}
import io.grpc._
import logsdb.grpc.StorageService
import logsdb.protos.replication.{ReplicateRequest, ReplicatorFs2Grpc, TransactionLog}
import logsdb.settings.{AppSettings, ServerSettings}
import logsdb.storage.RocksDB
import org.lyranthe.fs2_grpc.java_runtime.syntax.{ManagedChannelBuilderOps, ServerBuilderOps}

import scala.concurrent.duration.FiniteDuration

class ReplicaServerApp(R: RocksDB[IO], settings: AppSettings, primary: ServerSettings)(
  implicit CS: ContextShift[IO],
  T: Timer[IO]
) {

  def run: IO[Unit] = {
    val resources = for {
      grpc       <- createGRPCServer(R, settings.server.port)
      replicator <- createReplicatorServer(R, primary)
    } yield (grpc, replicator)

    resources.use {
      case (grpc, replications) =>
        IO(grpc.start()) *> replications.compile.drain
    }
  }

  private def createGRPCServer(R: RocksDB[IO], port: Int): Resource[IO, Server] = {
    val builder: ServerBuilder[_] =
      ServerBuilder
        .forPort(port)
        .addService(StorageService.built(R))

    new ServerBuilderOps(builder)
      .resource[IO]
  }

  private def createReplicatorServer(R: RocksDB[IO], primary: ServerSettings): Resource[IO, fs2.Stream[IO, Unit]] = {
    val channel = {
      val builder = ManagedChannelBuilder
        .forAddress(primary.host, primary.port)
        .usePlaintext()

      new ManagedChannelBuilderOps(builder).resource[IO]
    }

    val delay = Timer[IO].sleep(FiniteDuration(settings.replication.syncDelay.getOrElse(500), TimeUnit.MILLISECONDS))

    val errorAdapter: StatusRuntimeException => Option[Exception] =
      e => Option(e.getCause).map(e => new RuntimeException(e.getMessage))

    channel
      .map(ch => ReplicatorFs2Grpc.stub[IO](ch, errorAdapter = errorAdapter))
      .map { client =>
        def replicate(): fs2.Stream[IO, Unit] = {
          val transactions = for {
            sequenceNumber <- fs2.Stream.eval(R.latestSequenceNumber)
            transaction    <- client.replicate(ReplicateRequest(sequenceNumber), new Metadata())
          } yield transaction

          transactions.evalMap(t => insertTransactionLog(R, t)) ++ fs2.Stream.eval(delay) ++ replicate()
        }

        replicate()
      }
  }

  private def insertTransactionLog(R: RocksDB[IO], log: TransactionLog): IO[Unit] = {
    val record = log.records.head
    R.put(record.collection, record.key.toByteArray, record.value.toByteArray)
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
