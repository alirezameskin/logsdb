package logsdb.component.cluster.paxos

case class Acceptor[I: Ordering, V](
  highestPrepareSeen: Option[I] = None,
  highestAccepted: Option[AcceptedValue[I, V]] = None
) {
  private val ord = implicitly[Ordering[I]]

  def receive(cmd: Command[I, V]): (Acceptor[I, V], Action[I, V]) =
    cmd match {
      case PrepareCommand(from, number) if highestPrepareSeen.isEmpty =>
        (
          this.copy(highestPrepareSeen = Some(number)),
          Unicast(from, PromiseMessage(number, highestAccepted))
        )

      case PrepareCommand(from, number) if ord.gt(number, highestPrepareSeen.get) =>
        (
          this.copy(highestPrepareSeen = Some(number)),
          Unicast(from, PromiseMessage(number, highestAccepted))
        )

      case PrepareCommand(_, _) =>
        (this, Action.empty)

      case AcceptCommand(_, AcceptMessage(number, value)) if highestPrepareSeen.isEmpty =>
        val msg: AcceptedMessage[I, V] = AcceptedMessage(number, value)
        val state_ = this.copy(
          highestPrepareSeen = Some(number),
          highestAccepted = Some(AcceptedValue(number, value))
        )

        (state_, Broadcast(LearnerRole, msg))

      case AcceptCommand(_, AcceptMessage(number, value)) if ord.gteq(number, highestPrepareSeen.get) =>
        val msg = AcceptedMessage(number, value)
        val state_ = this.copy(
          highestPrepareSeen = Some(number),
          highestAccepted = Some(AcceptedValue(number, value))
        )

        (state_, Broadcast(LearnerRole, msg))

      case AcceptCommand(_, _) =>
        (this, Action.empty)
    }
}
