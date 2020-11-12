package logsdb.component

import java.util.concurrent.TimeUnit

import cats.Traverse
import cats.effect.concurrent.Ref
import cats.implicits._
import cats.effect.{ConcurrentEffect, ContextShift, IO, Resource, Sync, Timer}
import io.grpc.{ManagedChannelBuilder, Metadata, Server, ServerBuilder, ServerServiceDefinition}
import io.odin.Logger
import logsdb.component.cluster.paxos.{Message, Peer, ProposalId}
import logsdb.component.cluster.{ClusterMessageListener, ClusterStatus, ClusteringService, Node, PaxosMessenger}
import logsdb.error.InvalidClusterNodeId
import logsdb.protos.cluster.{ClusteringFs2Grpc, PingRequest}
import logsdb.settings.{ClusterSettings, NodeSettings}
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._

import scala.concurrent.duration._
import scala.util.Random

class Cluster(
  nodeId: String,
  setting: NodeSettings,
  status: Ref[IO, ClusterStatus[IO]],
  members: List[Node[IO]],
  logger: Logger[IO]
)(implicit val T: Timer[IO], CS: ContextShift[IO])
    extends ClusterMessageListener[IO] {
  implicit val proposalId = new ProposalId[Long] {
    override def next(c: Long): Long = c + 1

    override def next: Long = Random.nextInt(1000);
  }

  implicit val l         = logger
  val messenger          = new PaxosMessenger(members)
  implicit val nextDelay = FiniteDuration(Random.nextInt(20) + 5, TimeUnit.SECONDS)
  println(s"NExT DELAY $nextDelay")

  var paxosNode = cluster.paxos.Node.apply[IO, Long, String](nodeId, 2, messenger)

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

  def pingMembers(): IO[Unit] =
    Traverse[List].traverse(members)(node => pingMember(node).start) *> IO.unit

  def run: IO[Unit] =
    for {
      _ <- logger.trace(s"Members ${members}")
      _ <- logger.trace(s"Proposing data ${nodeId}")
      p <- paxosNode
      f <- p.propose(nodeId).start
      _ <- f.join
    } yield ()

  override def onMessage(from: Peer, message: Message[Long, String]): IO[Unit] =
    for {
      p <- paxosNode
      _ <- p.receiveMessage(from, message)
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

    setting.nodes.find(n => n.id == setting.id) match {
      case None => Resource.liftF(CE.raiseError(InvalidClusterNodeId()))
      case Some(node) =>
        val cluster           = new Cluster(node.id, node, Ref.unsafe(ClusterStatus(members)), members, logger)
        val clusteringService = ClusteringService.build[IO](cluster, logger)
        val builder: ServerBuilder[_] =
          ServerBuilder
            .forPort(node.port)
            .addService(clusteringService)

        builder
          .resource[IO]
          .evalMap(grpc => Sync[IO].pure(grpc.start()))
          .map(grpc => cluster)
    }
  }
}
