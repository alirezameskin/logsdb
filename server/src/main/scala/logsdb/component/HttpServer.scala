package logsdb.component

import cats.effect.{Blocker, ConcurrentEffect, ContextShift, IO, Resource, Timer}
import io.odin.Logger
import logsdb.component.http.{CollectionEndpoints, LogsEndpoints}
import logsdb.settings.HttpServerSettings
import logsdb.storage.RocksDB
import org.http4s.server.{Router, Server}
import org.http4s.server.blaze.BlazeServerBuilder
import org.http4s.implicits._
import org.http4s.server.middleware._
import org.http4s.server.staticcontent._

import scala.concurrent.ExecutionContext.global

class HttpServer(val server: Server[IO], logger: Logger[IO]) {
  def run: IO[Unit] = logger.info(s"HTTP Server started on : ${server.address}") *> IO.never
}

object HttpServer {
  def build(
    settings: HttpServerSettings,
    R: RocksDB[IO],
    blocker: Blocker
  )(implicit T: Timer[IO], CF: ConcurrentEffect[IO], L: Logger[IO], CS: ContextShift[IO]): Resource[IO, HttpServer] =
    BlazeServerBuilder[IO](global)
      .bindHttp(settings.port, settings.host)
      .withHttpApp(
        CORS(
          Router(
            "/v1/collections" -> CollectionEndpoints(R).endpoints,
            "/v1/logs"        -> LogsEndpoints(R).endpoints,
            "/ui"             -> resourceService[IO](ResourceService.Config("/ui", blocker))
          ).orNotFound
        )
      )
      .resource
      .map(r => new HttpServer(r, L))
}
