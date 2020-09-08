package logsdb

import io.circe._
import io.circe.generic.semiauto._

package object settings {
  implicit val serverSettings: Decoder[ServerSettings]         = deriveDecoder
  implicit val storageSettings: Decoder[StorageSettings]       = deriveDecoder
  implicit val replicationConfig: Decoder[ReplicationSettings] = deriveDecoder
  implicit val appSettings: Decoder[AppSettings]               = deriveDecoder
}
