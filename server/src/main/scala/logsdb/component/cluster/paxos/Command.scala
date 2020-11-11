package logsdb.component.cluster.paxos

sealed trait Command[I, V]

case class ProposeCommand[I, V](value: V)                                    extends Command[I, V]
case class PrepareCommand[I, V](from: Peer, number: I)                       extends Command[I, V]
case class PromiseCommand[I, V](from: Peer, message: PromiseMessage[I, V])   extends Command[I, V]
case class AcceptCommand[I, V](from: Peer, message: AcceptMessage[I, V])     extends Command[I, V]
case class AcceptedCommand[I, V](from: Peer, message: AcceptedMessage[I, V]) extends Command[I, V]
