package logsdb.component.cluster

import logsdb.component.cluster.paxos.ProposalId

case class LeaderElectionProposal(view: Long, epoc: Long)

object LeaderElectionProposal {
  implicit val proposalId = new ProposalId[LeaderElectionProposal] {
    override def next(c: LeaderElectionProposal): LeaderElectionProposal = c.copy(epoc = c.epoc + 1)

    override def next: LeaderElectionProposal = LeaderElectionProposal(0, 0)

    override def clear(c: LeaderElectionProposal): LeaderElectionProposal = c.copy(view = c.view + 1)

    override def view(c: LeaderElectionProposal): Long = c.view
  }

  implicit val ordering = new Ordering[LeaderElectionProposal] {
    override def compare(x: LeaderElectionProposal, y: LeaderElectionProposal): Int =
      if (x.view > y.view) {
        1
      } else if (x.view == y.view) {
        java.lang.Long.compare(x.epoc, y.epoc)
      } else {
        -1
      }
  }
}
