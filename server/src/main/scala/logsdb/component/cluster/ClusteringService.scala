package logsdb.component.cluster

import cats.effect.{ConcurrentEffect, Sync}
import cats.implicits._
import io.grpc.{Metadata, ServerServiceDefinition}
import io.odin.Logger
import logsdb.protos.cluster._

class ClusteringService[F[_]: Sync](listener: ClusterMessageListener[F], logger: Logger[F])
    extends ClusteringFs2Grpc[F, Metadata] {
  override def ping(request: PingRequest, ctx: Metadata): F[PingResponse] =
    logger.trace(s"Ping request from ${request}").map(_ => PingResponse())

  override def paxos(request: PaxosRequest, ctx: Metadata): F[PaxosResponse] =
    MessageEncoding.toMessage(request) match {
      case Some((from, message)) =>
        listener.onMessage(from, message) *> Sync[F].pure(PaxosResponse())
      case None =>
        logger.trace(s"Invalid message received ${request}") *> Sync[F].pure(PaxosResponse())
    }

}

object ClusteringService {
  def build[F[_]: ConcurrentEffect](listener: ClusterMessageListener[F], logger: Logger[F]): ServerServiceDefinition =
    ClusteringFs2Grpc.bindService(new ClusteringService[F](listener, logger))
}
