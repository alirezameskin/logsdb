package logsdb.component.grpc

import cats.Traverse
import cats.effect.concurrent.Ref
import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import fs2.Stream
import io.grpc._
import logsdb.error.InvalidQueryError
import logsdb.implicits._
import logsdb.protos._
import logsdb.query.LogRecordMatcher
import logsdb.storage.{Decoder, Encoder, RocksDB}

class StorageService(R: RocksDB[IO], N: Ref[IO, Int])(implicit CS: ContextShift[IO], T: Timer[IO])
    extends StorageFs2Grpc[IO, Metadata] {
  val DEFAULT_COLLECTION = "default"

  override def query(request: QueryParams, ctx: Metadata): fs2.Stream[IO, LogRecord] = {
    val to         = Option(request.to)
    val from       = Option(request.from).getOrElse(0L)
    val limit      = Option(request.limit).getOrElse(100).toLong
    val collection = Option(request.collection).filter(_.nonEmpty).getOrElse(DEFAULT_COLLECTION)
    val query      = Option(request.query).filter(_.nonEmpty).getOrElse("{}")

    LogRecordMatcher.build(query) match {
      case Left(error) =>
        fs2.Stream.raiseError[IO](InvalidQueryError(error))

      case Right(recordMatcher) =>
        R.startsWith[RecordId, LogRecord](collection, RecordId(from))
          .takeWhile(r => to.forall(_ >= r.timestamp.map(_.seconds).getOrElse(0L)))
          .filter(recordMatcher.matches)
          .take(limit)
    }
  }

  override def tail(request: QueryParams, ctx: Metadata): fs2.Stream[IO, LogRecord] = {
    val from       = Option(request.from).filter(_ != 0).map(RecordId(_)).flatMap(f => implicitly[Encoder[RecordId]].encode(f).toOption)
    val decoder    = implicitly[Decoder[LogRecord]]
    val collection = Option(request.collection).filter(_.nonEmpty).getOrElse(DEFAULT_COLLECTION)
    val query      = Option(request.query).filter(_.nonEmpty).getOrElse("{}")

    LogRecordMatcher.build(query) match {
      case Left(error) =>
        fs2.Stream.raiseError[IO](InvalidQueryError(error))

      case Right(recordMatcher) =>
        R.tail(collection, from)
          .map(bytes => decoder.decode(bytes))
          .map(_.toOption)
          .filter(_.isDefined)
          .map(_.get)
          .filter(recordMatcher.matches)
    }
  }

  override def push(request: Stream[IO, PushRequest], ctx: Metadata): Stream[IO, PushResponse] =
    request.evalMap { req =>
      val collection = Option(req.collection).filter(_.nonEmpty).getOrElse("default")

      Traverse[List]
        .traverse[IO, LogRecord, Unit](req.records.toList) { record =>
          for {
            nuance <- N.getAndUpdate(x => x + 1)
            id = record.timestamp
              .map(t => RecordId(t.seconds, t.nanos, nuance))
              .getOrElse(RecordId(java.time.Instant.now.getEpochSecond, java.time.Instant.now.getNano, nuance))

            _ <- R.put(collection, id, record.copy(id = Some(id)))
          } yield ()
        }
        .map(_ => PushResponse())
    }

  override def collections(request: GetCollectionsRequest, ctx: Metadata): IO[Collections] =
    R.collections.map(Collections.apply(_))
}

object StorageService {
  def built(R: RocksDB[IO])(implicit CS: ContextShift[IO], T: Timer[IO]): ServerServiceDefinition =
    StorageFs2Grpc.bindService(new StorageService(R, Ref.unsafe(0)))
}
