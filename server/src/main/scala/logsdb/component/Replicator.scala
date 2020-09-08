package logsdb.component

import java.util.concurrent.TimeUnit

import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import io.grpc.{ManagedChannelBuilder, Metadata, StatusRuntimeException}
import logsdb.protos.replication.{ReplicateRequest, ReplicatorFs2Grpc, TransactionLog}
import logsdb.settings.{AppSettings, ServerSettings}
import logsdb.storage.RocksDB
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._

import scala.concurrent.duration.FiniteDuration

class Replicator[F[_]: ContextShift: Timer: ConcurrentEffect](R: RocksDB[F], settings: AppSettings, primary: ServerSettings) {
  val delay = Timer[F].sleep(FiniteDuration(settings.replication.syncDelay.getOrElse(500), TimeUnit.MILLISECONDS))

  val errorAdapter: StatusRuntimeException => Option[Exception] = e =>
    Option(e.getCause).map(e => new RuntimeException(e.getMessage))

  def run: F[Unit] = replicate

  private def replicate: F[Unit] = {
    val builder: ManagedChannelBuilder[_] = ManagedChannelBuilder
      .forAddress(primary.host, primary.port)
      .usePlaintext()

    builder
      .resource[F]
      .map(ch => ReplicatorFs2Grpc.stub[F](ch, errorAdapter = errorAdapter))
      .use { client =>
        def doReplicate(): fs2.Stream[F, Unit] = {
          val transactions = for {
            sequenceNumber <- fs2.Stream.eval(R.latestSequenceNumber)
            transaction    <- client.replicate(ReplicateRequest(sequenceNumber), new Metadata())
          } yield transaction

          transactions.evalMap(t => insertTransactionLog(R, t)) ++ fs2.Stream.eval(delay) ++ doReplicate()
        }

        doReplicate().compile.drain
      }
  }

  private def insertTransactionLog(R: RocksDB[F], log: TransactionLog): F[Unit] = {
    val record = log.records.head
    R.put(record.collection, record.key.toByteArray, record.value.toByteArray)
  }
}

object Replicator {
  def build[F[_]: ContextShift: Timer: ConcurrentEffect](R: RocksDB[F], settings: AppSettings, primary: ServerSettings) =
    Resource.pure(new Replicator(R, settings, primary))
}
