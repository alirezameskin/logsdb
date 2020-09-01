package logsdb.grpc

import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO}
import fs2._
import io.grpc._
import logsdb.implicits._
import logsdb.protos._
import logsdb.storage.RocksDB

class PushService(R: RocksDB[IO], N: Ref[IO, Long]) extends PusherFs2Grpc[IO, Metadata] {

  override def push(request: Stream[IO, PushRequest], ctx: Metadata): Stream[IO, PushResponse] =
    request.evalMap { req =>
      for {
        nuance <- N.getAndUpdate(x => x + 1)
        _ <- req.record match {
          case Some(record) =>
            val collection = Option(req.collection).filter(_.nonEmpty).getOrElse("default")
            val id         = RecordId(record.time, nuance)
            R.put(collection, id, record.copy(id = Some(RecordId(record.time, nuance))))

          case None =>
            IO.pure()
        }
      } yield PushResponse()
    }

}

object PushService {
  def build(R: RocksDB[IO])(implicit CS: ContextShift[IO]): ServerServiceDefinition =
    PusherFs2Grpc.bindService(new PushService(R, Ref.unsafe(0L)))
}
