package logsdb

import cats.effect.{ExitCode, IO}
import com.monovore.decline._
import com.monovore.decline.effect.CommandIOApp

object ServerApp extends CommandIOApp(name = "logsdb", header = "LogsDB server", version = "0.1") {

  override def main: Opts[IO[ExitCode]] = ServerOptions.opts.map { options =>
    val result =
      if (options.settings.replication.isPrimary)
        PrimaryServerApp.run(options.settings)
      else
        ReplicaServerApp.run(options.settings)

    result.attempt
      .flatMap {
        case Right(_)  => IO(ExitCode.Success)
        case Left(err) => IO(println(fansi.Color.Red(err.getMessage))) *> IO(ExitCode.Error)
      }
  }

}
