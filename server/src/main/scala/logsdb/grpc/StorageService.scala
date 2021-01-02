package logsdb.grpc

import cats.effect.{ContextShift, IO, Timer}
import cats.implicits._
import fs2.Stream
import io.grpc._
import logsdb.command.{GetCollectionsCommand, GetRecords, PushRecords}
import logsdb.error.InvalidQueryError
import logsdb.implicits._
import logsdb.protos._
import logsdb.query.LogRecordMatcher
import logsdb.storage.{Decoder, Encoder}
import raft4s.Cluster

class StorageService(cluster: Cluster[IO])(implicit CS: ContextShift[IO], T: Timer[IO]) extends StorageFs2Grpc[IO, Metadata] {
  val DEFAULT_COLLECTION = "default"

  override def query(request: QueryParams, ctx: Metadata): fs2.Stream[IO, LogRecord] = {
    val to         = Option(request.to)
    val from       = RecordId(Option(request.from).getOrElse(0L))
    val limit      = Option(request.limit).getOrElse(100)
    val collection = Option(request.collection).filter(_.nonEmpty).getOrElse(DEFAULT_COLLECTION)
    val query      = Option(request.query).filter(_.nonEmpty).getOrElse("{}")

    for {
      prefix  <- fs2.Stream.eval(IO.fromEither(implicitly[Encoder[RecordId]].encode(from)))
      matcher <- fs2.Stream.eval(IO.fromEither(LogRecordMatcher.build(query).leftMap(InvalidQueryError)))
      result <- fs2.Stream
        .evalSeq(cluster.execute(GetRecords(collection, prefix, limit)))
        .evalMap { row =>
          IO.fromEither {
            implicitly[Decoder[PushRequest]].decode(row._2)
          }
        }
        .flatMap(req => fs2.Stream.apply(req.records: _*))
        .takeWhile(r => to.forall(_ >= r.timestamp.map(_.seconds).getOrElse(0L)))
        .filter(matcher.matches)
        .take(request.limit)
    } yield result
  }

  override def push(request: Stream[IO, PushRequest], ctx: Metadata): Stream[IO, PushResponse] =
    request.evalMap { req =>
      val collection = Option(req.collection).filter(_.nonEmpty).getOrElse("default")
      val timestamp  = req.records.headOption.flatMap(_.timestamp)
      val id = timestamp
        .map(t => RecordId(t.seconds, t.nanos))
        .getOrElse(RecordId(java.time.Instant.now.getEpochSecond, java.time.Instant.now.getNano))

      val result = for {
        key   <- implicitly[Encoder[RecordId]].encode(id)
        value <- implicitly[Encoder[PushRequest]].encode(req)
      } yield cluster.execute(PushRecords(collection, key, value)).map(_ => PushResponse())

      result match {
        case Left(error)  => IO.raiseError(error)
        case Right(value) => value
      }
    }

  override def collections(request: GetCollectionsRequest, ctx: Metadata): IO[Collections] =
    cluster.execute(GetCollectionsCommand()).map(Collections.apply(_))
}

object StorageService {
  def built(cluster: Cluster[IO])(implicit CS: ContextShift[IO], T: Timer[IO]): ServerServiceDefinition =
    StorageFs2Grpc.bindService(new StorageService(cluster))
}
