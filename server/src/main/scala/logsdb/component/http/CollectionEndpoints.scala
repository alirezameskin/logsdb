package logsdb.component.http

import cats.effect.Sync
import cats.implicits._
import logsdb.storage.RocksDB
import org.http4s.HttpRoutes
import org.http4s.dsl.Http4sDsl
import io.circe.syntax._
import logsdb.implicits._
import org.http4s.circe._

class CollectionEndpoints[F[_]: Sync](R: RocksDB[F]) extends Http4sDsl[F] {

  private def listEndpoint: HttpRoutes[F] = HttpRoutes.of[F] {
    case GET -> Root => R.collections.map(_.asJson).flatMap(list => Ok(list))
  }

  def endpoints: HttpRoutes[F] =
    listEndpoint
}

object CollectionEndpoints {
  def apply[F[_]: Sync](R: RocksDB[F]): CollectionEndpoints[F] = new CollectionEndpoints(R)
}
