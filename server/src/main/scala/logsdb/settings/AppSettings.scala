package logsdb.settings

import io.odin.Level

final case class AppSettings(logLevel: Level, storage: StorageSettings, server: ServerSettings, replication: ReplicationSettings)
