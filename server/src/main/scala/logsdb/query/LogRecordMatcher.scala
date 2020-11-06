package logsdb.query

import logsdb.protos.LogRecord

trait LogRecordMatcher {
  def matches(record: LogRecord): Boolean
}
