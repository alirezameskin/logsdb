package logsdb

import cats.effect.{ContextShift, IO, Resource, Timer}
import io.grpc.{Server, ServerBuilder}
import io.odin.Logger
import logsdb.grpc.StorageService
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._
import raft4s.Cluster

class GrpcServer(grpc: Server, logger: Logger[IO]) {
  def run: IO[Unit] = logger.info(s"GRPC Server started on Port: ${grpc.getPort}") *> IO.never
}

object GrpcServer {

  def build(
    cluster: Cluster[IO],
    port: Int
  )(implicit CS: ContextShift[IO], T: Timer[IO], L: Logger[IO]): Resource[IO, GrpcServer] = {
    val builder: ServerBuilder[_] =
      ServerBuilder
        .forPort(port)
        .addService(StorageService.built(cluster))

    builder
      .resource[IO]
      .evalMap(grpc => IO(grpc.start()))
      .map(grpc => new GrpcServer(grpc, L))
  }

}
