package logsdb.component

import java.util.concurrent.TimeUnit

import cats.Traverse
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.effect.{ConcurrentEffect, ContextShift, IO, Resource, Sync, Timer}
import io.grpc.{ManagedChannelBuilder, Metadata, Server, ServerBuilder}
import io.odin.Logger
import logsdb.component.cluster.paxos.ProposalId
import logsdb.component.cluster.{ClusterStatus, ClusteringService, Node, PaxosMessenger}
import logsdb.error.InvalidClusterNodeId
import logsdb.protos.cluster.{ClusteringFs2Grpc, PingRequest}
import logsdb.settings.{ClusterSettings, NodeSettings}
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._

import scala.concurrent.duration._

class Cluster(
  nodeId: String,
  grpc: Server,
  setting: NodeSettings,
  status: Ref[IO, ClusterStatus[IO]],
  members: List[Node[IO]],
  logger: Logger[IO]
)(implicit val T: Timer[IO], CS: ContextShift[IO]) {
  implicit val proposalId = new ProposalId[Int] {
    override def next(c: Int): Int = c + 1

    override def next: Int = 1;
  }

  implicit val l         = logger
  val messenger          = new PaxosMessenger(members)
  implicit val nextDelay = FiniteDuration(20, TimeUnit.SECONDS)

  def pingMember(member: Node[IO], errorCount: Int = 0): IO[Unit] =
    for {
      _   <- logger.trace(s"Sending ping message to member ${member.id}, errors : ${errorCount}")
      res <- member.client.use(_.ping(PingRequest(setting.id), new Metadata())).attempt
      _   <- status.modify(configConnection(member, res.isRight || errorCount < 5, _))
      sts <- status.get
      _   <- logger.trace(s"Current cluster Status ${sts}")
      _   <- T.sleep(10.seconds)
      _   <- pingMember(member, if (res.isLeft) errorCount + 1 else 0)
    } yield ()

  def configConnection(node: Node[IO], connected: Boolean, status: ClusterStatus[IO]): (ClusterStatus[IO], Unit) = {

    val nodes = status.nodes.map {
      case n if n.id == node.id => println(s" ${node.id} is Connected? ${connected}"); n.copy(connected = connected)
      case n                    => n
    }

    (status.copy(nodes = nodes), ())
  }

  def runPaxos(): IO[Unit] =
    for {
      node <- cluster.paxos.Node.apply[IO, Int, String](nodeId, 2, messenger)
      n    <- node.propose(nodeId).start
    } yield ()

  def pingMembers(): IO[Unit] =
    runPaxos() *> Traverse[List].traverse(members)(node => pingMember(node).start) *> IO.unit

  def run: IO[Unit] =
    logger.trace(s"Members ${members}") *> pingMembers()
}

object Cluster {
  def build(
    setting: ClusterSettings
  )(implicit T: Timer[IO], logger: Logger[IO], CS: ContextShift[IO], CE: ConcurrentEffect[IO]): Resource[IO, Cluster] = {

    val members: List[Node[IO]] = setting.nodes
      .filterNot(_.id == setting.id)
      .map { setting =>
        val builder: ManagedChannelBuilder[_] = ManagedChannelBuilder
          .forAddress(setting.host, setting.port)
          .usePlaintext()

        val client: Resource[IO, ClusteringFs2Grpc[IO, Metadata]] =
          new ManagedChannelBuilderOps(builder).resource[IO].map(channel => ClusteringFs2Grpc.stub(channel))

        Node[IO](setting.host, setting.port, setting.id, false, client)
      }

    setting.nodes.find(n => n.id == setting.id) match {
      case None => Resource.liftF(CE.raiseError(InvalidClusterNodeId()))
      case Some(node) =>
        val builder: ServerBuilder[_] =
          ServerBuilder
            .forPort(node.port)
            .addService(ClusteringService.build[IO](logger))

        builder
          .resource[IO]
          .evalMap(grpc => Sync[IO].pure(grpc.start()))
          .map(grpc => new Cluster(node.id, grpc, node, Ref.unsafe(ClusterStatus(members)) /* TODO */, members, logger))
    }
  }
}
