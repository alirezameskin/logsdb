package logsdb

import java.io.File

import cats.data.Validated
import cats.implicits._
import com.monovore.decline.Opts
import com.typesafe.config.ConfigFactory
import logsdb.settings.AppSettings

import scala.util.{Failure, Success, Try}

case class ServerOptions(settings: AppSettings)

object ServerOptions {

  private val settingsOpts: Opts[AppSettings] =
    Opts
      .option[String]("config", "Configuration file", "c")
      .mapValidated { path =>
        Try(new File(path)) match {
          case Failure(exception)            => Validated.invalidNel(exception.getMessage)
          case Success(file) if !file.exists => Validated.invalidNel(s"$path Does not exist")
          case Success(file)                 => Validated.valid(file)
        }
      }
      .mapValidated { file =>
        Try(ConfigFactory.parseFile(file)).toEither.leftMap(_.getMessage).toValidatedNel
      }
      .mapValidated { config =>
        io.circe.config.parser.decode[AppSettings](config).leftMap(_.getMessage).toValidatedNel
      }

  def opts: Opts[ServerOptions] =
    (settingsOpts).map(ServerOptions.apply)
}
