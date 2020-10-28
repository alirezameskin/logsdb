package logsdb.component.http

import cats.effect.Sync
import cats.implicits._
import logsdb.storage.RocksDB
import org.http4s.{HttpRoutes, QueryParamDecoder}
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._
import logsdb.implicits._
import logsdb.protos.{LogRecord, RecordId}
import org.http4s.circe._

class LogsEndpoints[F[_]: Sync](R: RocksDB[F]) extends Http4sDsl[F] {
  case class Limit(size: Long)

  val IdPatternSeconds      = """([0-9]+)""".r
  val IdPatternSecondsNanos = """([0-9]+).([0-9]+)""".r
  val IdPatternFull         = """([0-9]+).([0-9]+).([0-9]+)""".r

  implicit val limitQueryParamDecoder: QueryParamDecoder[Limit] = QueryParamDecoder[Long].map(Limit)

  implicit val fromQueryParamDecoder: QueryParamDecoder[RecordId] = QueryParamDecoder[String].map {
    case IdPatternSeconds(seconds)             => RecordId(seconds.toLong)
    case IdPatternSecondsNanos(seconds, nanos) => RecordId(seconds.toLong, nanos.toInt)
    case IdPatternFull(seconds, nanos, nuance) => RecordId(seconds.toLong, nanos.toInt, nuance.toInt)
  }

  object AfterMatcher extends QueryParamDecoderMatcher[RecordId]("after")
  object LimitMatcher extends OptionalQueryParamDecoderMatcher[Limit]("limit")

  private def listEndpoint: HttpRoutes[F] = HttpRoutes.of[F] {

    case GET -> Root / "tail" / collection :? LimitMatcher(limit) =>
      R.last[RecordId, LogRecord](collection)
        .take(limit.map(_.size).getOrElse(100L))
        .compile
        .toList
        .map(_.reverse)
        .map(_.asJson)
        .flatMap(items => Ok(items))

    case GET -> Root / collection :? AfterMatcher(after) +& LimitMatcher(limit) =>
      R.startsWith[RecordId, LogRecord](collection, after)
        .filterNot(_.id.contains(after))
        .take(limit.map(_.size).getOrElse(100L))
        .compile
        .toList
        .map(_.asJson)
        .flatMap(items => Ok(items))
  }

  def endpoints: HttpRoutes[F] =
    listEndpoint
}

object LogsEndpoints {
  def apply[F[_]: Sync](R: RocksDB[F]): LogsEndpoints[F] = new LogsEndpoints(R)
}
