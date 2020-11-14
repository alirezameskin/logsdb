package logsdb.component.cluster

import logsdb.component.cluster.paxos._
import logsdb.protos.cluster._

object MessageEncoding {

  def toMessage(request: PaxosRequest): Option[(String, Message[LeaderElectionProposal, String])] =
    request match {
      case PrepareRequest(from, view, number, _) =>
        Some((from, PrepareMessage(LeaderElectionProposal(view, number))))

      case PromiseRequest(from, view, number, prev, _) =>
        Some(
          (
            from,
            PromiseMessage(
              LeaderElectionProposal(view, number),
              prev.map(a => AcceptedValue(LeaderElectionProposal(a.view, a.number), a.value))
            )
          )
        )

      case AcceptRequest(from, view, number, value, _) =>
        Some((from, AcceptMessage(LeaderElectionProposal(view, number), value)))

      case AcceptedRequest(from, view, number, value, _) =>
        Some((from, AcceptedMessage(LeaderElectionProposal(view, number), value)))

      case RejectRequest(from, view, number, prev, _) =>
        Some(
          (
            from,
            RejectMessage(
              LeaderElectionProposal(view, number),
              prev.map(a => AcceptedValue(LeaderElectionProposal(a.view, a.number), a.value))
            )
          )
        )

      case _ => None
    }

  def toRequest(from: String, message: Message[LeaderElectionProposal, String]): PaxosRequest =
    message match {
      case PrepareMessage(proposal) =>
        PrepareRequest(from, proposal.view, proposal.epoc)

      case PromiseMessage(proposal, prevAccepted) =>
        PromiseRequest(
          from,
          proposal.view,
          proposal.epoc,
          prevAccepted.map(d => AcceptedValueRequest(d.number.view, d.number.epoc, d.value))
        )

      case RejectMessage(proposal, prevAccepted) =>
        RejectRequest(
          from,
          proposal.view,
          proposal.epoc,
          prevAccepted.map(d => AcceptedValueRequest(d.number.view, d.number.epoc, d.value))
        )

      case AcceptMessage(proposal, value) =>
        AcceptRequest(from, proposal.view, proposal.epoc, value)

      case AcceptedMessage(proposal, value) =>
        AcceptedRequest(from, proposal.view, proposal.epoc, value)
    }
}
