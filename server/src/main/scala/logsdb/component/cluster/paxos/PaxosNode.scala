package logsdb.component.cluster.paxos

import cats._
import cats.effect.concurrent.{Deferred, Ref}
import cats.effect.{Concurrent, ContextShift, Timer}
import cats.implicits._
import io.odin.Logger

import scala.concurrent.duration.FiniteDuration

class PaxosNode[F[_]: Monad: Timer: ContextShift: Concurrent: Logger, I: Ordering: ProposalId, V](
  val peer: String,
  quorumSize: Int,
  proposer: Ref[F, Proposer[I, V]],
  acceptor: Ref[F, Acceptor[I, V]],
  learner: Ref[F, Learner[I, V]],
  chosen: Deferred[F, V],
  messenger: Messenger[F, I, V]
) {

  val proposalId = implicitly[ProposalId[I]]

  def listen: F[V] = chosen.get

  def clear(): F[Unit] =
    for {
      p <- proposer.get
      n = p.number.map(proposalId.clear)
      _ <- proposer.getAndUpdate(p => p.copy(membersPromised = List.empty, chosen = None, number = n))
      _ <- acceptor.set(Acceptor[I, V]())
      _ <- learner.set(LearnerLearning[I, V](quorumSize))
    } yield ()

  def propose(value: V)(implicit nextDelay: FiniteDuration): F[V] =
    Concurrent[F].race(doPropose(value, nextDelay), listen).map {
      case Right(value) => value
      case Left(value)  => value
    }

  private def doPropose(value: V, nextDelay: FiniteDuration): F[V] =
    for {
      _      <- Logger[F].info(s"${peer} Starts proposing value ${value}")
      action <- proposer.modify(_.receive(ProposeCommand(value)))
      _      <- doAction(action)
      v      <- rePropose(value, nextDelay)
    } yield v

  private def rePropose(value: V, nextDelay: FiniteDuration): F[V] =
    learner.get
      .flatMap { l =>
        if (l.finished) Monad[F].pure(l.value.get)
        else {
          Timer[F].sleep(nextDelay) *> doPropose(value, nextDelay)
        }
      }

  def receiveMessage(from: String, msg: Message[I, V]): F[Unit] =
    msg match {
      case PrepareMessage(number) =>
        for {
          _      <- Logger[F].info(s"${peer} Received ${msg} from ${from}")
          action <- acceptor.modify(_.receive(PrepareCommand(from, number)))
          _      <- doAction(action)
        } yield ()

      case msg: PromiseMessage[I, V] =>
        for {
          _      <- Logger[F].info(s"${peer} Received ${msg} from ${from}")
          action <- proposer.modify(_.receive(PromiseCommand(from, msg)))
          _      <- Logger[F].info(s"Do action ${action}")
          _      <- doAction(action)
        } yield ()

      case msg: RejectMessage[I, V] =>
        for {
          _      <- Logger[F].info(s"${peer} Received ${msg} from ${from}")
          action <- proposer.modify(_.receive(RejectCommand(from, msg)))
          p      <- proposer.get
          _      <- Logger[F].info(s"Do action ${action}")
          _      <- doAction(action)

        } yield ()

      case msg: AcceptMessage[I, V] =>
        for {
          _      <- Logger[F].info(s"${peer} Received ${msg} from ${from}")
          action <- acceptor.modify(_.receive(AcceptCommand(from, msg)))
          _      <- Logger[F].info(s"Do action ${action}")
          _      <- doAction(action)
        } yield ()

      case msg: AcceptedMessage[I, V] =>
        for {
          _ <- Logger[F].info(s"${peer} Received ${msg} from ${from}")
          l <- learner.updateAndGet(_.receive(AcceptedCommand(from, msg)))
          _ <- if (l.finished) chosen.complete(l.value.get)
          else Concurrent[F].pure(())
        } yield ()
    }

  private def doAction(action: Action[I, V]): F[Unit] = action match {
    case Broadcast(_, message) =>
      for {
        _ <- Logger[F].info(s"${peer} broadcasting ${message}")
        _ <- messenger.broadcast(this.peer, message)
      } yield ()

    case Unicast(node, message) =>
      for {
        _ <- Logger[F].info(s"${peer} sending ${message} to ${node}")
        _ <- messenger.unicast(this.peer, node, message)
      } yield ()

    case NoAction =>
      Concurrent[F].pure(())
  }

}

object PaxosNode {

  def apply[F[_]: Concurrent: Timer: ContextShift: Logger, I: Ordering: ProposalId, V](
    id: String,
    quorumSize: Int,
    messenger: Messenger[F, I, V]
  ): F[PaxosNode[F, I, V]] =
    for {
      proposer <- Ref.of[F, Proposer[I, V]](Proposer[I, V](quorumSize))
      acceptor <- Ref.of[F, Acceptor[I, V]](Acceptor[I, V]())
      learner  <- Ref.of[F, Learner[I, V]](LearnerLearning[I, V](quorumSize))
      chosen   <- Deferred[F, V]
    } yield new PaxosNode[F, I, V](id, quorumSize, proposer, acceptor, learner, chosen, messenger)
}
