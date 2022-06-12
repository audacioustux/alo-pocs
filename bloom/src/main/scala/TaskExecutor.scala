import org.graalvm.polyglot.*
import org.graalvm.polyglot.io.ByteSequence

import java.nio.file.{Files, Path}

@main def testMultiple(): Unit =
  val wasmBinary1 = Files.readAllBytes(Path.of("./src/main/wasm/dummy1.opt.wasm"))
  val wasmSource1 = Source.newBuilder("wasm", ByteSequence.create(wasmBinary1), "dummy1.wasm").build()

  val executor = new TaskExecutor
  executor.eval(wasmSource1)
  print(executor.execute("main", "run"))

class TaskExecutor {

  import TaskExecutor._

  private final val threadLocalCtx: ThreadLocal[Context] = ThreadLocal.withInitial(() =>
    Context.newBuilder()
      .option("wasm.Builtins", "wasi_snapshot_preview1")
      .engine(engine)
      .build())

  def eval(source: Source): Object =
    threadLocalCtx.get().eval(source)

  def execute(sourceName: String, methodName: String): Object =
    threadLocalCtx.get().getBindings("wasm").getMember(sourceName).getMember(methodName).execute()

}

object TaskExecutor {
  private final val engine = Engine.create("wasm")
}
