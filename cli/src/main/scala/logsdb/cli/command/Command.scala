package logsdb.cli.command

import cats.effect.{ContextShift, IO}
import com.monovore.decline.Opts

trait Command {
  type OPTIONS

  def options: Opts[OPTIONS]

  def execute(options: OPTIONS)(implicit CS: ContextShift[IO]): IO[Unit]
}
