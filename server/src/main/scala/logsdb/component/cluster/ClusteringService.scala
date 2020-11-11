package logsdb.component.cluster

import cats.implicits._
import cats.effect.{ConcurrentEffect, ContextShift, IO, Sync}
import io.grpc.{Metadata, ServerServiceDefinition}
import io.odin.Logger
import logsdb.protos.cluster.{ClusteringFs2Grpc, PaxosMessage, PaxosResponse, PingRequest, PingResponse}

class ClusteringService[F[_]: Sync](logger: Logger[F]) extends ClusteringFs2Grpc[F, Metadata] {
  override def ping(request: PingRequest, ctx: Metadata): F[PingResponse] =
    logger.trace(s"Ping request from ${request}").map(_ => PingResponse())

  override def paxos(request: PaxosMessage, ctx: Metadata): F[PaxosResponse] =
    logger.trace(s"Paxos Message ${request}").map(_ => PaxosResponse())
}

object ClusteringService {
  def build[F[_]: ConcurrentEffect](L: Logger[F]): ServerServiceDefinition =
    ClusteringFs2Grpc.bindService(new ClusteringService[F](L))
}
