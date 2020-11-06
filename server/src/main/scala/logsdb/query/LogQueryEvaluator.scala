package logsdb.query

import logql.parser.AST.{LogQueryExpr, MatchEqual, MatchNotEqual, MatchNotRegexp, MatchRegexp}

trait LogQueryEvaluator {
  def evaluate(line: String, labels: Map[String, String]): Boolean
}

object LogQueryEvaluator {
  def build(ast: LogQueryExpr): LogQueryEvaluator = {
    val labelEvaluator  = buildLabelEvaluator(ast)
    val filterEvaluator = buildFilterEvaluator(ast)

    (line: String, labels: Map[String, String]) => labelEvaluator.evaluate(line, labels) && filterEvaluator.evaluate(line, labels)
  }

  def buildLabelEvaluator(ast: LogQueryExpr): LogQueryEvaluator = {

    val labelEvaluators: List[LogQueryEvaluator] = ast.labels.map {
      case MatchEqual(name, pattern) =>
        (_: String, labels: Map[String, String]) => labels.contains(name) && labels.get(name).contains(pattern)

      case MatchNotEqual(name, pattern) =>
        (_: String, labels: Map[String, String]) => labels.contains(name) && !labels.get(name).contains(pattern)

      case MatchRegexp(name, pattern) =>
        (_: String, labels: Map[String, String]) => labels.contains(name) && labels.get(name).forall(_.matches(pattern))

      case MatchNotRegexp(name, pattern) =>
        (_: String, labels: Map[String, String]) => labels.contains(name) && !labels.get(name).forall(_.matches(pattern))
    }

    (line: String, labels: Map[String, String]) => labelEvaluators.forall(_.evaluate(line, labels))
  }

  def buildFilterEvaluator(ast: LogQueryExpr): LogQueryEvaluator = { (_, _) =>
    true
  }
}
