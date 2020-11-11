package logsdb.component.cluster.paxos

sealed trait Message[I, V]
case class PrepareMessage[I, V](number: I)                                                   extends Message[I, V]
case class PromiseMessage[I, V](number: I, prevAccepted: Option[AcceptedValue[I, V]] = None) extends Message[I, V]
case class AcceptMessage[I, V](number: I, value: V)                                          extends Message[I, V]
case class AcceptedMessage[I, V](number: I, value: V)                                        extends Message[I, V]

case class AcceptedValue[I, V](number: I, value: V)

trait ProposalId[T] {
  def next(c: T): T
  def next: T
}
