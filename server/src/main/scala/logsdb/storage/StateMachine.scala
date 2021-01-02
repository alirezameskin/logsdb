package logsdb.storage

import cats.effect.IO
import cats.effect.concurrent.Ref
import logsdb.command.{GetCollectionsCommand, GetRecords, PushRecords}
import logsdb.protos.Collection
import raft4s.{ReadCommand, WriteCommand}

import java.nio.ByteBuffer

class StateMachine(R: RocksDB[IO], last: Ref[IO, Long]) extends raft4s.StateMachine[IO] {

  override def applyWrite: PartialFunction[(Long, WriteCommand[_]), IO[Any]] = {
    case (index, cmd: PushRecords) =>
      push(index, cmd) *> last.set(index) *> IO.unit
  }

  override def applyRead: PartialFunction[ReadCommand[_], IO[Any]] = {
    case GetCollectionsCommand() => getCollections
    case cmd: GetRecords         => query(cmd)
  }

  override def appliedIndex: IO[Long] =
    last.get

  override def takeSnapshot(): IO[(Long, ByteBuffer)] =
    IO((0L, ByteBuffer.wrap(Array.emptyByteArray)))

  override def restoreSnapshot(index: Long, bytes: ByteBuffer): IO[Unit] =
    IO.unit

  private def push(index: Long, request: PushRecords): IO[Unit] = {
    val buffer = ByteBuffer.allocate(8)
    buffer.putLong(index)
    val indexBytes = buffer.array()

    val key = request.key ++ indexBytes
    R.put(request.collection, key, request.value)
  }

  private def getCollections: IO[Seq[Collection]] =
    R.collections

  private def query(request: GetRecords): IO[List[(Array[Byte], Array[Byte])]] =
    R.startsWith(request.collection, request.after)
      .take(request.size)
      .compile
      .toList
}

object StateMachine {
  def build(R: RocksDB[IO]): IO[StateMachine] =
    for {
      last <- Ref.of[IO, Long](0L)
    } yield new StateMachine(R, last)
}
