package logsdb.settings

final case class AppSettings(storage: StorageSettings, server: ServerSettings, replication: ReplicationSettings)
