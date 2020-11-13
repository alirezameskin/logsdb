package logsdb.component.cluster

import cats.Parallel
import cats.effect.{ContextShift, IO}
import cats.implicits._
import io.grpc.Metadata
import logsdb.component.cluster.paxos._
import logsdb.protos.cluster

class PaxosMessenger(members: List[Node[IO]])(implicit CS: ContextShift[IO]) extends Messenger[IO, Long, String] {

  override def unicast(from: String, to: String, msg: Message[Long, String]): IO[Unit] =
    members.find(_.id == to).map(_.client) match {
      case None => IO.unit
      case Some(client) =>
        val message = msg match {
          case PrepareMessage(number) =>
            cluster.PrepareMessage(from, number)

          case PromiseMessage(number, prevAccepted) =>
            cluster.PromiseMessage(from, number, prevAccepted.map(d => cluster.AcceptedValue(d.number, d.value)))

          case AcceptMessage(number, value) =>
            cluster.AcceptMessage(from, number, value)

          case AcceptedMessage(number, value) =>
            cluster.AcceptedMessage(from, number, value)
        }

        client.use { c =>
          c.paxos(message, new Metadata()) *> IO.unit
        }
    }

  override def broadcast(from: String, msg: Message[Long, String]): IO[Unit] =
    Parallel.parTraverse(members)(n => unicast(from, n.id, msg)).start *> IO.unit
}
