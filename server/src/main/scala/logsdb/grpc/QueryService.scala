package logsdb.grpc

import cats.effect.{ContextShift, IO}
import io.grpc._
import logsdb.LogId
import logsdb.protos.{LogRecord, QueryFs2Grpc, QueryParams}
import logsdb.storage.RocksDB

class QueryService(R: RocksDB[IO]) extends QueryFs2Grpc[IO, Metadata] {

  override def query(request: QueryParams, ctx: Metadata): fs2.Stream[IO, LogRecord] = {
    val start = Option(request.from).getOrElse(0L)
    val limit = Option(request.limit).getOrElse(100)

    for {
      itr    <- fs2.Stream.eval(R.startsWith[LogId, LogRecord](LogId(start)))
      stream <- fs2.Stream.fromIterator[IO](itr).take(limit)
    } yield stream
  }

}

object QueryService {
  def built(R: RocksDB[IO])(implicit CS: ContextShift[IO]): ServerServiceDefinition =
    QueryFs2Grpc.bindService(new QueryService(R))
}
