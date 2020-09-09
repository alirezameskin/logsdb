package logsdb

import cats.effect.{ExitCode, IO}
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp
import io.odin.formatter.Formatter
import io.odin.{consoleLogger, Logger}

object ServerApp extends CommandIOApp(name = "logsdb", header = "LogsDB server", version = "0.1") {

  override def main: Opts[IO[ExitCode]] = ServerOptions.opts.map { options =>
    implicit val logger: Logger[IO] = consoleLogger[IO](formatter = Formatter.colorful, minLevel = options.settings.logLevel)

    val result =
      if (options.settings.replication.isPrimary)
        PrimaryServerApp.run(options.settings)
      else
        ReplicaServerApp.run(options.settings)

    result.attempt
      .flatMap {
        case Right(_)  => IO(ExitCode.Success)
        case Left(err) => logger.error(err.getMessage) *> IO(println(fansi.Color.Red(err.getMessage))) *> IO(ExitCode.Error)
      }
  }

}
