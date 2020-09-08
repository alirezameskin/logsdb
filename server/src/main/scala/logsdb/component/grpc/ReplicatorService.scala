package logsdb.component.grpc

import cats.effect.{ContextShift, IO, Timer}
import io.grpc.{Metadata, ServerServiceDefinition}
import logsdb.protos.replication.{ReplicateRequest, ReplicatorFs2Grpc, TransactionLog}
import logsdb.storage.RocksDB

class ReplicatorService(R: RocksDB[IO]) extends ReplicatorFs2Grpc[IO, Metadata] {
  override def replicate(request: ReplicateRequest, ctx: Metadata): fs2.Stream[IO, TransactionLog] =
    R.transactionsSince(request.sequenceNumber)
}

object ReplicatorService {

  def built(R: RocksDB[IO])(implicit CS: ContextShift[IO], T: Timer[IO]): ServerServiceDefinition =
    ReplicatorFs2Grpc.bindService(new ReplicatorService(R))
}
