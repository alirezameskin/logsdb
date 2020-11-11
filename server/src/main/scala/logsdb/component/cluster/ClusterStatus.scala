package logsdb.component.cluster

case class ClusterStatus[F[_]](nodes: List[Node[F]], leader: Option[Node[F]] = None)
