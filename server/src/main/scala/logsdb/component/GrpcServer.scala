package logsdb.component

import cats.effect.{ContextShift, IO, Resource, Timer}
import io.grpc.{Server, ServerBuilder}
import logsdb.component.grpc.{ReplicatorService, StorageService}
import logsdb.storage.RocksDB
import org.lyranthe.fs2_grpc.java_runtime.syntax.all._

class GrpcServer(grpc: Server) {
  def run: IO[Unit] = IO.never
}

object GrpcServer {

  def buildReplicaServer(R: RocksDB[IO], port: Int)(implicit CS: ContextShift[IO], T: Timer[IO]): Resource[IO, GrpcServer] = {
    val builder: ServerBuilder[_] =
      ServerBuilder
        .forPort(port)
        .addService(StorageService.built(R))

    builder
      .resource[IO]
      .evalMap(grpc => IO(grpc.start()))
      .map(grpc => new GrpcServer(grpc))

  }

  def buildPrimaryServer(R: RocksDB[IO], port: Int)(implicit CS: ContextShift[IO], T: Timer[IO]): Resource[IO, GrpcServer] = {
    val builder: ServerBuilder[_] =
      ServerBuilder
        .forPort(port)
        .addService(StorageService.built(R))
        .addService(ReplicatorService.built(R))

    builder
      .resource[IO]
      .evalMap(grpc => IO(grpc.start()))
      .map(grpc => new GrpcServer(grpc))
  }

}
