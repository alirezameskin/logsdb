package logsdb.component.cluster

import cats.Parallel
import cats.effect.{ContextShift, IO}
import cats.implicits._
import io.grpc.Metadata
import logsdb.component.cluster.paxos._

class PaxosMessenger(members: List[Node[IO]])(implicit CS: ContextShift[IO])
    extends Messenger[IO, LeaderElectionProposal, String] {

  override def unicast(from: String, to: String, msg: Message[LeaderElectionProposal, String]): IO[Unit] =
    members.find(_.id == to).map(_.client) match {
      case None => IO.unit
      case Some(client) =>
        val request = MessageEncoding.toRequest(from, msg)

        client.use { c =>
          c.paxos(request, new Metadata()) *> IO.unit
        }
    }

  override def broadcast(from: String, msg: Message[LeaderElectionProposal, String]): IO[Unit] =
    Parallel.parTraverse(members)(n => unicast(from, n.id, msg).attempt).start *> IO.unit
}
