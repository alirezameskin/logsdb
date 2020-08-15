package logsdb

import cats.implicits._
import com.monovore.decline.Opts

case class ServerOptions(port: Int, path: String)

object ServerOptions {

  private val portOpts: Opts[Int] =
    Opts.option[Int]("port", "Port", short = "p").orElse(Opts(9092))

  private val pathOpts: Opts[String] =
    Opts.option[String]("path", "Database Path", short = "d").orElse(Opts("/tmp/logsdb"))

  def opts: Opts[ServerOptions] =
    (portOpts, pathOpts).mapN(ServerOptions.apply)
}
