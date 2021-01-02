package logsdb.command

import raft4s.ReadCommand

case class GetRecords(collection: String, after: Array[Byte], size: Int) extends ReadCommand[List[(Array[Byte], Array[Byte])]]
