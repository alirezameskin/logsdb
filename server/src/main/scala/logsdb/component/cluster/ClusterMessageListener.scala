package logsdb.component.cluster

import logsdb.component.cluster.paxos.Message

trait ClusterMessageListener[F[_]] {

  def onMessage(from: String, message: Message[LeaderElectionProposal, String]): F[Unit]
}
