package logsdb.cli.instances

import cats.implicits._
import com.google.protobuf.struct.NullValue.NULL_VALUE
import com.google.protobuf.struct.Value.Kind
import com.google.protobuf.struct.{ListValue, Struct, Value}
import io.circe.Decoder.Result
import io.circe.{Decoder, Encoder, HCursor, Json}
import io.circe.syntax._

trait StructInstances {

  implicit val decodeStruct = new Decoder[Struct] {
    override def apply(c: HCursor): Result[Struct] =
      if (c.value.isObject)
        c.value.asObject
          .map(_.toMap)
          .getOrElse(Map.empty)
          .map(itm => decodeValue.apply(itm._2.hcursor).map(v => (itm._1, v)))
          .toList
          .sequence
          .map(fs => Struct(fs.toMap))
      else
        Struct().asRight
  }

  implicit val decodeKind = new Decoder[Kind] {
    override def apply(c: HCursor): Result[Kind] = c.value match {
      case json if json.isNull    => Kind.NullValue(NULL_VALUE).asRight
      case json if json.isNumber  => Kind.NumberValue(json.asNumber.map(_.toDouble).get).asRight
      case json if json.isString  => Kind.StringValue(json.asString.get).asRight
      case json if json.isBoolean => Kind.BoolValue(json.asBoolean.get).asRight

      case json if json.isObject =>
        json.asObject.get.toMap
          .map(i => apply(i._2.hcursor).map(k => (i._1, Value(k))))
          .toList
          .sequence
          .map(fields => Kind.StructValue(Struct(fields.toMap)))

      case json if json.isArray =>
        json.asArray.get
          .map(j => apply(j.hcursor).map(k => Value(k)))
          .toList
          .sequence
          .map(items => Kind.ListValue(ListValue(items)))

      case _ => Kind.Empty.asRight
    }
  }

  implicit val decodeValue = new Decoder[Value] {
    override def apply(c: HCursor): Result[Value] = decodeKind.apply(c).map(k => Value(k))
  }

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
