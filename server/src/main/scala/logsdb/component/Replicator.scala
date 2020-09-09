package logsdb.component

import java.util.concurrent.TimeUnit

import cats.MonadError
import cats.effect.{ConcurrentEffect, ContextShift, Resource, Timer}
import cats.implicits._
import io.grpc.{ManagedChannelBuilder, Metadata, StatusRuntimeException}
import io.odin.Logger
import logsdb.protos.replication.{ReplicateRequest, ReplicatorFs2Grpc, TransactionLog}
import logsdb.settings.{AppSettings, ServerSettings}
import logsdb.storage.RocksDB
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._

import scala.concurrent.duration.FiniteDuration

class Replicator[F[_]: ContextShift: Timer: ConcurrentEffect: Logger](
  R: RocksDB[F],
  settings: AppSettings,
  primary: ServerSettings
) {
  val logger         = Logger[F]
  val pullDelay      = Timer[F].sleep(FiniteDuration(settings.replication.syncDelay.getOrElse(500), TimeUnit.MILLISECONDS))
  val reconnectDelay = Timer[F].sleep(FiniteDuration(5, TimeUnit.SECONDS))

  val errorAdapter: StatusRuntimeException => Option[Exception] = e =>
    Option(e.getCause).map(e => new RuntimeException(e.getMessage))

  def run: F[Unit] =
    MonadError[F, Throwable].handleErrorWith(replicate) { err =>
      val message = s"""Error during replication "${err.getMessage}". Trying in 5 seconds"""

      logger.warn(message) *> reconnectDelay *> run
    }

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

          transactions.evalMap(t => insertTransactionLog(R, t)) ++ fs2.Stream.eval(pullDelay) ++ doReplicate()
        }

        logger.info("Starting to replicate") *> doReplicate().compile.drain
      }
  }

  private def insertTransactionLog(R: RocksDB[F], log: TransactionLog): F[Unit] = {
    val record = log.records.head
    R.put(record.collection, record.key.toByteArray, record.value.toByteArray)
  }
}

object Replicator {
  def build[F[_]: ContextShift: Timer: ConcurrentEffect: Logger](
    R: RocksDB[F],
    settings: AppSettings,
    primary: ServerSettings
  ): Resource[F, Replicator[F]] =
    Resource.pure[F, Replicator[F]](new Replicator(R, settings, primary))
}
