package logsdb.component.cluster.paxos

case class Proposer[I: Ordering: ProposalId, V](
  quorum: Int,
  value: Option[V] = None,
  number: Option[I] = None,
  membersPromised: List[String] = List.empty,
  highestAccepted: Option[AcceptedValue[I, V]] = None,
  chosen: Option[AcceptedValue[I, V]] = None
) {

  val ord: Ordering[I] = implicitly[Ordering[I]]
  val proposalId       = implicitly[ProposalId[I]]

  def receive(cmd: Command[I, V]): (Proposer[I, V], Action[I, V]) =
    cmd match {
      case ProposeCommand(_) if chosen.isDefined =>
        (this, Action.empty)

      case ProposeCommand(v) =>
        val number_ = number match {
          case Some(n) => proposalId.next(n)
          case None    => proposalId.next
        }
        val state_ = this.copy(
          number = Some(number_),
          value = Some(v),
          membersPromised = List.empty,
          highestAccepted = None
        )
        val prepareMessage: PrepareMessage[I, V] = PrepareMessage(number_)
        val action: Action[I, V]                 = Broadcast(AcceptorRole, prepareMessage)

        (state_, action)

      case PromiseCommand(_, _) if number.isEmpty =>
        (this, Action.empty)

      case PromiseCommand(_, msg) if ord.lt(msg.number, number.get) =>
        (this, Action.empty)

      case PromiseCommand(_, msg) if ord.gt(msg.number, number.get) =>
        (this, Action.empty)

      case PromiseCommand(node, _) if membersPromised.contains(node) =>
        (this, Action.empty)

      case PromiseCommand(node, msg) =>
        val selectedAccepted = this.highestAccepted match {
          case Some(hav) =>
            msg.prevAccepted match {
              case Some(v) if ord.gt(hav.number, v.number)   => Some(hav)
              case Some(v) if ord.lteq(hav.number, v.number) => Some(v)
              case None                                      => Some(hav)
            }
          case None => msg.prevAccepted
        }

        val state_ = this.copy(
          membersPromised = this.membersPromised.appended(node),
          highestAccepted = selectedAccepted
        )

        if (state_.membersPromised.size != quorum) {
          (state_, Action.empty)
        } else {
          val chosen_ =
            state_.highestAccepted
              .getOrElse(AcceptedValue(number.get, value.get))
          (
            state_.copy(value = Some(chosen_.value), chosen = Some(chosen_)),
            Broadcast(AcceptorRole, AcceptMessage(number.get, chosen_.value))
          )
        }

      case RejectCommand(_, RejectMessage(number, Some(accepted))) if ord.gteq(accepted.number, number) =>
        val number_ = proposalId.next(accepted.number)
        val state_ = this.copy(
          number = Some(number_),
          value = Some(accepted.value),
          membersPromised = List.empty,
          highestAccepted = None
        )
        val prepareMessage: PrepareMessage[I, V] = PrepareMessage(number_)
        val action: Action[I, V]                 = Broadcast(AcceptorRole, prepareMessage)

        (state_, action)

      case RejectCommand(_, _) =>
        (this, Action.empty)
    }
}
