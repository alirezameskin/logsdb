package logsdb.component.cluster.paxos

sealed trait Learner[I, V] {
  def quorum: Int
  def finished: Boolean
  def value: Option[V]
  def receive(cmd: Command[I, V]): Learner[I, V]
}

case class LearnerDecided[I, V](quorum: Int, chosen: V) extends Learner[I, V] {
  override def finished         = true
  override def value: Option[V] = Some(chosen)

  override def receive(cmd: Command[I, V]): Learner[I, V] = cmd match {
    case _ => this
  }
}

case class LearnerLearning[I: Ordering, V](
  quorum: Int,
  acceptors: Map[String, AcceptedValue[I, V]] = Map.empty[String, AcceptedValue[I, V]],
  accepted: Map[I, Set[String]] = Map.empty[I, Set[String]]
) extends Learner[I, V] {
  private val ord: Ordering[I] = implicitly[Ordering[I]]

  override def finished: Boolean = false

  override def value: Option[V] = None

  override def receive(cmd: Command[I, V]): Learner[I, V] = cmd match {
    case AcceptedCommand(peer, msg) if !acceptors.contains(peer) =>
      val acceptedValue = AcceptedValue(msg.number, msg.value)
      val acceptors_    = acceptors + (peer -> acceptedValue)
      val peers_        = accepted.getOrElse(msg.number, Set.empty).incl(peer)
      val accepted_     = accepted + (msg.number -> peers_)
      val learner_      = this.copy(acceptors = acceptors_, accepted = accepted_)

      if (learner_.accepted(msg.number).size == quorum)
        LearnerDecided(quorum, msg.value)
      else learner_

    case AcceptedCommand(peer, msg) if acceptors.contains(peer) =>
      if (ord.gt(acceptors(peer).number, msg.number)) {
        this
      } else {
        val oldAccepted   = acceptors(peer).number
        val acceptedValue = AcceptedValue(msg.number, msg.value)
        val acceptors_    = acceptors + (peer -> acceptedValue)
        val accepted_     = accepted - (oldAccepted)

        val learner_ = this.copy(acceptors = acceptors_, accepted = accepted_)

        if (learner_.accepted(msg.number).size == quorum)
          LearnerDecided(quorum, msg.value)
        else learner_
      }

    case _ => this
  }

}
