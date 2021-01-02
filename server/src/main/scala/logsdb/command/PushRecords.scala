package logsdb.command

import raft4s.WriteCommand

case class PushRecords(collection: String, key: Array[Byte], value: Array[Byte]) extends WriteCommand[Unit]
