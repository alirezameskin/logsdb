package logsdb.command

import logsdb.protos.Collection
import raft4s.ReadCommand

case class GetCollectionsCommand() extends ReadCommand[List[Collection]]
