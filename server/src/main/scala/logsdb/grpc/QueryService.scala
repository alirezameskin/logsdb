package logsdb.grpc

import java.util.concurrent.TimeUnit

import cats.effect.{Blocker, ContextShift, IO, Timer}
import io.grpc._
import logsdb.protos.{LogRecord, QueryFs2Grpc, QueryParams, RecordId}
import logsdb.storage.{Decoder, Encoder, RocksDB}
import logsdb.implicits._

import scala.concurrent.duration.FiniteDuration

class QueryService(R: RocksDB[IO])(implicit CS: ContextShift[IO], T: Timer[IO]) extends QueryFs2Grpc[IO, Metadata] {

  override def query(request: QueryParams, ctx: Metadata): fs2.Stream[IO, LogRecord] = {
    val to    = Option(request.to)
    val from  = Option(request.from).getOrElse(0L)
    val limit = Option(request.limit).getOrElse(100)

    R.startsWith[RecordId, LogRecord](RecordId(from))
      .takeWhile(r => to.forall(_ >= r.time))
      .take(limit)
  }

  override def tail(request: QueryParams, ctx: Metadata): fs2.Stream[IO, LogRecord] = {
    val from    = Option(request.from).filter(_ != 0).map(RecordId(_)).flatMap(f => implicitly[Encoder[RecordId]].encode(f).toOption)
    val delay   = FiniteDuration(500, TimeUnit.MILLISECONDS)
    val decoder = implicitly[Decoder[LogRecord]]

    R.tail(100, delay, from)
      .void
      .stream
      .map(bytes => decoder.decode(bytes))
      .map(_.toOption)
      .filter(_.isDefined)
      .map(_.get)
  }

}

object QueryService {
  def built(R: RocksDB[IO])(implicit CS: ContextShift[IO], T: Timer[IO]): ServerServiceDefinition =
    QueryFs2Grpc.bindService(new QueryService(R))
}
