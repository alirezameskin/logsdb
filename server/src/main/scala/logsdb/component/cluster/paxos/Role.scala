package logsdb.component.cluster.paxos

sealed trait Role
case object ProposerRole extends Role
case object AcceptorRole extends Role
case object LearnerRole  extends Role
