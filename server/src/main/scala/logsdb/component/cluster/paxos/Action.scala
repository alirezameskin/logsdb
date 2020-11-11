package logsdb.component.cluster.paxos

sealed trait Action[+I, +V]

case class Broadcast[I, V](role: Role, message: Message[I, V]) extends Action[I, V]
case class Unicast[I, V](node: Peer, message: Message[I, V])   extends Action[I, V]
case object NoAction                                           extends Action[Nothing, Nothing]

object Action {
  def empty[I, V]: Action[I, V] = NoAction
}
