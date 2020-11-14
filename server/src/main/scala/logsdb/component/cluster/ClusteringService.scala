package logsdb.component.cluster

import cats.effect.{ConcurrentEffect, Sync}
import cats.implicits._
import io.grpc.{Metadata, ServerServiceDefinition}
import io.odin.Logger
import logsdb.component.cluster.{paxos => p}
import logsdb.protos.cluster._

class ClusteringService[F[_]: Sync](listener: ClusterMessageListener[F], logger: Logger[F])
    extends ClusteringFs2Grpc[F, Metadata] {
  override def ping(request: PingRequest, ctx: Metadata): F[PingResponse] =
    logger.trace(s"Ping request from ${request}").map(_ => PingResponse())

  override def paxos(request: PaxosMessage, ctx: Metadata): F[PaxosResponse] =
    toMsg(request) match {
      case Some((from, message)) =>
        listener.onMessage(from, message) *> Sync[F].pure(PaxosResponse())
      case None =>
        logger.trace(s"Invalid message received ${request}") *> Sync[F].pure(PaxosResponse())
    }

  private def toMsg(request: PaxosMessage): Option[(String, p.Message[Long, String])] =
    request match {
      case PrepareMessage(from, number, _) =>
        Some((from, p.PrepareMessage(number)))

      case PromiseMessage(from, number, prev, _) =>
        Some((from, p.PromiseMessage(number, prev.map(a => p.AcceptedValue(a.number, a.value)))))

      case AcceptMessage(from, number, value, _) =>
        Some((from, p.AcceptMessage(number, value)))

      case AcceptedMessage(from, number, value, _) =>
        Some((from, p.AcceptedMessage(number, value)))

      case RejectMessage(from, number, prev, _) =>
        Some((from, p.RejectMessage(number, prev.map(a => p.AcceptedValue(a.number, a.value)))))

      case _ => None
    }
}

object ClusteringService {
  def build[F[_]: ConcurrentEffect](listener: ClusterMessageListener[F], logger: Logger[F]): ServerServiceDefinition =
    ClusteringFs2Grpc.bindService(new ClusteringService[F](listener, logger))
}
