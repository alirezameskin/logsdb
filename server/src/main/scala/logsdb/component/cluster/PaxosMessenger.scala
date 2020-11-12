package logsdb.component.cluster

import cats.implicits._
import cats.Traverse
import cats.effect.IO
import io.grpc.Metadata
import logsdb.component.cluster.paxos.{AcceptMessage, AcceptedMessage, Message, Messenger, Peer, PrepareMessage, PromiseMessage}
import logsdb.protos.cluster

class PaxosMessenger(members: List[Node[IO]]) extends Messenger[IO, Long, String] {

  override def unicast(from: Peer, to: Peer, msg: Message[Long, String]): IO[Unit] =
    members.find(_.id == to.id).map(_.client) match {
      case None => IO.unit
      case Some(client) =>
        val message = msg match {
          case PrepareMessage(number) =>
            cluster.PrepareMessage(from.id, number)
          case PromiseMessage(number, prevAccepted) =>
            cluster.PromiseMessage(from.id, number, prevAccepted.map(d => cluster.AcceptedValue(d.number, d.value)))
          case AcceptMessage(number, value) =>
            cluster.AcceptMessage(from.id, number, value)
          case AcceptedMessage(number, value) =>
            cluster.AcceptedMessage(from.id, number, value)
        }

        client.use { c =>
          c.paxos(message, new Metadata()) *> IO.unit
        }
    }

  override def broadcast(from: Peer, msg: Message[Long, String]): IO[Unit] =
    Traverse[List].traverse(members)(m => unicast(from, Peer(m.id), msg)) *> IO.unit
}
