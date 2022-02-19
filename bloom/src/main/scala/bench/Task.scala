package bench

import org.graalvm.polyglot._

trait Executable[T]:
  extension (task: T) def execute(): Any

given Executable[Source] with
  extension (t: Source) def execute(): Any = 2

object Task {
  private val engine = Engine
    .newBuilder()
    .option("engine.Mode", "throughput")
    .build()
  private val polyCtxBuilder = Context
    .newBuilder()
    .allowAllAccess(true)
    .engine(engine)

  def apply[T: Executable](task: T): Task =
    new Task()
}

private class Task() {
  val a = 2

  def execute(): Int = a
}
