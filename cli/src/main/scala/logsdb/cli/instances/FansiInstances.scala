package logsdb.cli.instances

import cats.Show
import fansi.Str

trait FansiInstances {
  implicit val fansiStrShow: Show[fansi.Str] = (t: Str) => t.render
}
