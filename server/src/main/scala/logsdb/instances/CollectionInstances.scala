package logsdb.instances

import io.circe.{Encoder, Json}
import logsdb.protos.Collection

trait CollectionInstances {

  implicit val collectionEncoder: Encoder[Collection] = new Encoder[Collection] {
    override def apply(c: Collection): Json = Json.obj(
      ("id", Json.fromInt(c.id)),
      ("name", Json.fromString(c.name)),
      ("size", Json.fromLong(c.size))
    )
  }

}
