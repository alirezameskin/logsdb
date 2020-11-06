package logsdb.error

case class InvalidQueryError(message: String) extends RuntimeException(message)
