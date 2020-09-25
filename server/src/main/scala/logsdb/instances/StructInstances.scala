package logsdb.instances

import com.google.protobuf.struct.Value.Kind
import com.google.protobuf.struct.{Struct, Value}
import io.circe.syntax._
import io.circe.{Encoder, Json}

trait StructInstances {

  implicit val encodeValue = new Encoder[Value] {
    override def apply(a: Value): Json = encodeKind(a.kind)
  }

  implicit val encodeKind = new Encoder[Kind] {
    override def apply(a: Kind): Json = a match {
      case Kind.Empty               => Json.Null
      case Kind.NullValue(_)        => Json.Null
      case Kind.NumberValue(num)    => num.asJson
      case Kind.StringValue(str)    => str.asJson
      case Kind.BoolValue(bool)     => bool.asJson
      case Kind.ListValue(items)    => Json.arr(items.values.map(i => apply(i.kind)): _*)
      case Kind.StructValue(struct) => Json.obj(struct.fields.map(i => (i._1, apply(i._2.kind))).toSeq: _*)
    }
  }

  implicit val encodeStruct = new Encoder[Struct] {
    override def apply(a: Struct): Json =
      a.fields.map {
        case (field, value) =>
          (field, value.asJson)
      }.asJson
  }
}
