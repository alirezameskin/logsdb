package logsdb.settings

import io.odin.Level

final case class AppSettings(
  logLevel: Level,
  storage: StorageSettings,
  server: ServerSettings,
  http: HttpServerSettings,
  replication: ReplicationSettings,
  cluster: ClusterSettings
)
