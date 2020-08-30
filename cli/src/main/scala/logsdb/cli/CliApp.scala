package logsdb.ingest

import cats.effect.{ExitCode, IO}
import com.monovore.decline.Opts
import com.monovore.decline.effect.CommandIOApp
import logsdb.cli.command._

object CliApp extends CommandIOApp(name = "logcli", "CLI tool to logsdb") {

  val pushOptions: Opts[PushOptions]   = PushCommand.options
  val queryOptions: Opts[QueryOptions] = QueryCommand.options
  val tailOptions: Opts[TailOptions]   = TailCommand.options

  override def main: Opts[IO[ExitCode]] = pushOptions.orElse(queryOptions).orElse(tailOptions).map {
    case options: PushOptions  => PushCommand.execute(options)
    case options: QueryOptions => QueryCommand.execute(options)
    case options: TailOptions  => TailCommand.execute(options)
  }
}
