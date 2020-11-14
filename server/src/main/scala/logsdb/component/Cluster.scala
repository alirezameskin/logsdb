package logsdb.component

import java.util.concurrent.TimeUnit

import cats.effect.{ConcurrentEffect, ContextShift, IO, Resource, Sync, Timer}
import io.grpc.{ManagedChannelBuilder, Metadata, ServerBuilder}
import io.odin.Logger
import logsdb.component.cluster._
import logsdb.component.cluster.paxos.{Message, PaxosNode}
import logsdb.error.InvalidClusterNodeId
import logsdb.protos.cluster.ClusteringFs2Grpc
import logsdb.settings.{ClusterSettings, NodeSettings}
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._

import scala.concurrent.duration._
import scala.util.Random

class Cluster(setting: NodeSettings, paxos: PaxosNode[IO, LeaderElectionProposal, String], logger: Logger[IO])(
  implicit val T: Timer[IO],
  CS: ContextShift[IO]
) {

  implicit val nextDelay = FiniteDuration(Random.nextInt(20) + 5, TimeUnit.SECONDS)

  def run: IO[Unit] =
    for {
      f <- paxos.propose(setting.id)
      _ <- logger.trace(s"Final decision ${f}")
    } yield ()
}

object Cluster {
  def build(
    setting: ClusterSettings
  )(implicit T: Timer[IO], logger: Logger[IO], CS: ContextShift[IO], CE: ConcurrentEffect[IO]): Resource[IO, Cluster] = {

    val members: List[Node[IO]] = setting.nodes
      .map { setting =>
        val builder: ManagedChannelBuilder[_] = ManagedChannelBuilder
          .forAddress(setting.host, setting.port)
          .usePlaintext()

        val client: Resource[IO, ClusteringFs2Grpc[IO, Metadata]] =
          new ManagedChannelBuilderOps(builder).resource[IO].map(channel => ClusteringFs2Grpc.stub(channel))

        Node[IO](setting.host, setting.port, setting.id, false, client)
      }

    val messenger = new PaxosMessenger(members)

    setting.nodes.find(n => n.id == setting.id) match {
      case None =>
        Resource.liftF(CE.raiseError(InvalidClusterNodeId()))

      case Some(settings) =>
        val quorumSize = (members.size / 2) + 1
        val res = for {
          paxos <- Resource.liftF(PaxosNode[IO, LeaderElectionProposal, String](setting.id, quorumSize, messenger))
          service = ClusteringService
            .build[IO](
              (from: String, message: Message[LeaderElectionProposal, String]) => paxos.receiveMessage(from, message),
              logger
            )
        } yield (service, paxos)

        res.flatMap {
          case (service, paxos) =>
            val builder: ServerBuilder[_] =
              ServerBuilder
                .forPort(settings.port)
                .addService(service)

            builder
              .resource[IO]
              .evalMap(grpc => Sync[IO].pure(grpc.start()))
              .map(grpc => new Cluster(settings, paxos, logger))

        }

    }
  }
}
