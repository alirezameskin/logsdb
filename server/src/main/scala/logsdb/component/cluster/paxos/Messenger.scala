package logsdb.component.cluster.paxos

trait Messenger[F[_], I, V] {
  def unicast(from: Peer, to: Peer, msg: Message[I, V]): F[Unit]
  def broadcast(from: Peer, msg: Message[I, V]): F[Unit]
}
