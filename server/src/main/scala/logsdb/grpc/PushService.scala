package logsdb.grpc

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import fs2._
import io.grpc._
import logsdb.protos._
import logsdb.storage.RocksDB
import logsdb.implicits._

class PushService(R: RocksDB[IO], N: Ref[IO, Long]) extends PusherFs2Grpc[IO, Metadata] {

  override def push(request: Stream[IO, LogRecord], ctx: Metadata): Stream[IO, PushResponse] =
    request.evalMap { req =>
      for {
        nuance <- N.getAndUpdate(x => x + 1)
        id  = RecordId(req.time, nuance)
        rec = req.copy(id = Some(id))
        _ <- R.put(id, rec)
      } yield PushResponse()
    }

}

object PushService {
  def build(R: RocksDB[IO])(implicit CS: ContextShift[IO]): ServerServiceDefinition =
    PusherFs2Grpc.bindService(new PushService(R, Ref.unsafe(0L)))
}
