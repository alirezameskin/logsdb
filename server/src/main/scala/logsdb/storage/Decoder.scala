package logsdb.storage

import java.nio.charset.StandardCharsets

import cats.implicits._

trait Decoder[A] extends Serializable{
  def decode(bytes: Array[Byte]): Decoder.Result[A]
}

object Decoder {
  final type Result[A] = Either[Throwable, A]

  implicit val stringDecoder = new Decoder[String] {
    override def decode(bytes: Array[Byte]): Result[String] =
      new String(bytes, StandardCharsets.UTF_8).asRight[Throwable]
  }
}