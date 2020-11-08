package logsdb.component

import cats.Traverse
import cats.implicits._
import cats.effect.{ConcurrentEffect, ContextShift, IO, Resource, Sync, Timer}
import io.grpc.{ManagedChannelBuilder, Metadata, Server, ServerBuilder}
import io.odin.Logger
import logsdb.component.cluster.{ClusteringService, Node}
import logsdb.error.InvalidClusterNodeId
import logsdb.protos.cluster.{ClusteringFs2Grpc, PingRequest}
import logsdb.settings.{ClusterSettings, NodeSettings}
import org.lyranthe.fs2_grpc.java_runtime.syntax.ManagedChannelBuilderOps
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._

import scala.concurrent.duration._

class Cluster(
  grpc: Server,
  setting: NodeSettings,
  members: List[Node[IO]],
  logger: Logger[IO]
)(implicit val T: Timer[IO], CS: ContextShift[IO]) {
  def pingMember(member: Node[IO], errorCount: Int = 0): IO[Unit] =
    for {
      _   <- logger.info(s"Sending ping message to member ${member.id}, errors : ${errorCount}")
      res <- member.client.use(_.ping(PingRequest(setting.id), new Metadata())).attempt
      _   <- T.sleep(10.seconds)
      _   <- pingMember(member, if (res.isLeft) errorCount + 1 else 0)
    } yield ()

  def pingMembers(): IO[Unit] =
    Traverse[List].traverse(members) { node =>
      pingMember(node).start
    } *> IO.unit

  def run: IO[Unit] =
    logger.info(s"Members ${members}") *> pingMembers()
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

        Node[IO](setting.host, setting.port, setting.id, client)
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
          .map(grpc => new Cluster(grpc, node, members, logger))
    }
  }
}
