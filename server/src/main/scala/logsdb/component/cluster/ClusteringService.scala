package logsdb.component.cluster

import cats.implicits._
import cats.effect.{ConcurrentEffect, ContextShift, IO, Sync}
import io.grpc.{Metadata, ServerServiceDefinition}
import io.odin.Logger
import logsdb.protos.cluster.{ClusteringFs2Grpc, PingRequest, PingResponse}

class ClusteringService[F[_]: Sync](logger: Logger[F]) extends ClusteringFs2Grpc[F, Metadata] {
  override def ping(request: PingRequest, ctx: Metadata): F[PingResponse] =
    logger.info(s"Ping request from ${request}").map(_ => PingResponse())
}

object ClusteringService {
  def build[F[_]: ConcurrentEffect](L: Logger[F]): ServerServiceDefinition =
    ClusteringFs2Grpc.bindService(new ClusteringService[F](L))
}
