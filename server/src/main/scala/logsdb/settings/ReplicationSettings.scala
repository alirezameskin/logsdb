package logsdb.settings

final case class ReplicationSettings(isPrimary: Boolean, syncDelay: Option[Long], primary: Option[ServerSettings])
