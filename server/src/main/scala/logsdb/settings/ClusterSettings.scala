package logsdb.settings

final case class ClusterSettings(host: String, port: Int, path: String, seed: Option[Address] = None)
