package logsdb.query

import java.util.regex.Pattern

import logql.parser.AST._

trait LogQueryEvaluator {
  def evaluate(line: String, labels: Map[String, String]): Boolean
}

object LogQueryEvaluator {

  def build(ast: LogQueryExpr): LogQueryEvaluator = {
    val labelEvaluator  = buildLabelEvaluator(ast)
    val filterEvaluator = buildFilterEvaluator(ast)

    (line: String, labels: Map[String, String]) => labelEvaluator.evaluate(line, labels) && filterEvaluator.evaluate(line, labels)
  }

  private def buildLabelEvaluator(ast: LogQueryExpr): LogQueryEvaluator = {

    val evaluators: List[LogQueryEvaluator] = ast.labels.map {
      case MatchEqual(name, pattern) =>
        (_: String, labels: Map[String, String]) => labels.contains(name) && labels.get(name).contains(pattern)

      case MatchNotEqual(name, pattern) =>
        (_: String, labels: Map[String, String]) => labels.contains(name) && !labels.get(name).contains(pattern)

      case MatchRegexp(name, regex) =>
        val pattern = Pattern.compile(regex)

        (_: String, labels: Map[String, String]) =>
          labels.contains(name) && labels.get(name).forall(s => pattern.matcher(s).find())

      case MatchNotRegexp(name, regex) =>
        val pattern = Pattern.compile(regex)

        (_: String, labels: Map[String, String]) =>
          labels.contains(name) && !labels.get(name).forall(s => pattern.matcher(s).find())
    }

    (line: String, labels: Map[String, String]) => evaluators.forall(_.evaluate(line, labels))
  }

  private def buildFilterEvaluator(ast: LogQueryExpr): LogQueryEvaluator = {

    val evaluators: List[LogQueryEvaluator] = ast.filters.map {
      case LineFilterExpr(matchType, matchStr) => buildLineFilterEvaluator(matchType, matchStr)
      case _                                   => (_: String, _: Map[String, String]) => true
    }

    (line: String, labels: Map[String, String]) => evaluators.forall(_.evaluate(line, labels))
  }

  private def buildLineFilterEvaluator(typ: LineFilterMatchType, str: String): LogQueryEvaluator =
    typ match {
      case ContainsString =>
        (line: String, _: Map[String, String]) => line.contains(str)

      case ContainsNotString =>
        (line: String, _: Map[String, String]) => !line.contains(str)

      case ContainsRegex =>
        val pattern = Pattern.compile(str)
        (line: String, _: Map[String, String]) => pattern.matcher(line).find()

      case ContainsNotRegex =>
        val pattern = Pattern.compile(str)
        (line: String, _: Map[String, String]) => !pattern.matcher(line).find()
    }
}
