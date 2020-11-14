package logsdb.component.cluster.paxos

sealed trait Command[I, V]

case class ProposeCommand[I, V](value: V)                                      extends Command[I, V]
case class PrepareCommand[I, V](from: String, number: I)                       extends Command[I, V]
case class PromiseCommand[I, V](from: String, message: PromiseMessage[I, V])   extends Command[I, V]
case class AcceptCommand[I, V](from: String, message: AcceptMessage[I, V])     extends Command[I, V]
case class AcceptedCommand[I, V](from: String, message: AcceptedMessage[I, V]) extends Command[I, V]
case class RejectCommand[I, V](from: String, message: RejectMessage[I, V])     extends Command[I, V]
