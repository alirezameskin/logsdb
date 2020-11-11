package logsdb.component.cluster

import cats.effect.Resource
import io.grpc.Metadata
import logsdb.protos.cluster.ClusteringFs2Grpc

case class Node[F[_]](
  host: String,
  port: Int,
  id: String,
  connected: Boolean,
  client: Resource[F, ClusteringFs2Grpc[F, Metadata]]
)
