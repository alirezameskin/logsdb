package logsdb.storage

trait Encoder[A] extends Serializable {
  def encode(a: A): Either[Throwable, Array[Byte]]
}
