package logsdb.component.cluster

import logsdb.component.cluster.paxos.{Message, Peer}

trait ClusterMessageListener[F[_]] {

  def onMessage(from: Peer, message: Message[Long, String]): F[Unit]
}
