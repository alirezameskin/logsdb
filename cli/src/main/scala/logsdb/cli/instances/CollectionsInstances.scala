package logsdb.cli.instances

import cats.Show
import logsdb.cli.utils.{BytesUtils, Tabulator}
import logsdb.protos.Collections

trait CollectionsInstances {
  implicit val collectionsShow = new Show[Collections] {
    override def show(obj: Collections): String = {
      val header = List(" Collection Id ", " Collection Name ", " Collection Size (Bytes) ", " Collection Size ")
      val rows = obj.collections.map { c =>
        List(c.id.toString, c.name, c.size.toString, BytesUtils.humanReadableSize(c.size))
      }

      Tabulator.format(List(header).appendedAll(rows))
    }
  }

}
