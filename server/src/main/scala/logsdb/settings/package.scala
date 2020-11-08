package logsdb

import io.circe._
import io.circe.generic.semiauto._
import io.odin.Level

package object settings {
  implicit val serverSettings: Decoder[ServerSettings]         = deriveDecoder
  implicit val storageSettings: Decoder[StorageSettings]       = deriveDecoder
  implicit val replicationConfig: Decoder[ReplicationSettings] = deriveDecoder
  implicit val httpServerSettings: Decoder[HttpServerSettings] = deriveDecoder
  implicit val nodeSettings: Decoder[NodeSettings]             = deriveDecoder
  implicit val clusterConfig: Decoder[ClusterSettings]         = deriveDecoder

  implicit val levelConfig2: Decoder[Level] = (c: HCursor) =>
    c.value.as[String].map(_.toLowerCase).flatMap {
      case "trace" => Right(Level.Trace)
      case "debug" => Right(Level.Debug)
      case "info"  => Right(Level.Info)
      case "warn"  => Right(Level.Warn)
      case "error" => Right(Level.Error)
      case x       => Left(DecodingFailure(s"Invalid Log Level: ${x}", List.empty))
    }

  implicit val appSettings: Decoder[AppSettings] = deriveDecoder
}
