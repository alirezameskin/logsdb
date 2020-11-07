package logsdb.query

import logql.parser.LogQueryParser
import logsdb.protos.LogRecord

trait LogRecordMatcher {
  def matches(record: LogRecord): Boolean
}

object LogRecordMatcher {

  def build(query: String): Either[String, LogRecordMatcher] =
    for {
      ast <- LogQueryParser.parse(query)
      evaluator = LogQueryEvaluator.build(ast)

    } yield (rec: LogRecord) => evaluator.evaluate(rec.message, rec.labels)
}
