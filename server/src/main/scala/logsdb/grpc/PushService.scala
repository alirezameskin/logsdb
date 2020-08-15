package logsdb.grpc

import cats.effect.{ContextShift, IO}
import io.grpc._
import fs2._
import logsdb.LogId
import logsdb.protos._
import logsdb.storage.RocksDB

class PushService(R: RocksDB[IO]) extends PusherFs2Grpc[IO, Metadata] {

  override def push(request: Stream[IO, LogRecord], ctx: Metadata): Stream[IO, PushResponse] =
    request.evalMap { req =>
      val id = LogId(req.time, Some(System.nanoTime()))
      R.put(id, req).map(_ => PushResponse())
    }

}

object PushService {
  def build(R: RocksDB[IO])(implicit CS: ContextShift[IO]): ServerServiceDefinition =
    PusherFs2Grpc.bindService(new PushService(R))
}
