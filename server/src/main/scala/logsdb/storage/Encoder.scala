package logsdb
package storage

trait Encoder[A] extends Serializable {
  def encode(a: A): Either[Throwable, Array[Byte]]
}

object Encoder {
  implicit val stringEncoder = new Encoder[String] {
    override def encode(a: String): Either[Throwable, Array[Byte]] = Right(a.getBytes)
  }
}