package logsdb.component.cluster.paxos

trait Messenger[F[_], I, V] {
  def unicast(from: String, to: String, msg: Message[I, V]): F[Unit]
  def broadcast(from: String, msg: Message[I, V]): F[Unit]
}
