package logsdb.storage

trait Decoder[A] extends Serializable {
  def decode(bytes: Array[Byte]): Decoder.Result[A]
}

object Decoder {
  final type Result[A] = Either[Throwable, A]
}
