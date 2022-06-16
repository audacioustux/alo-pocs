package bench

import org.graalvm.polyglot._
import org.graalvm.polyglot.io.ByteSequence
import scala.util.Random
import java.nio.file.{Files, Path}

object wasmMemoryMeasure {
  def main(args: Array[String]): Unit =
    Thread.sleep(10000)
    val engine = Engine.create()
    val ctx: Context = Context.newBuilder().engine(engine).build()
    val wasmBinary = Files.readAllBytes(Path.of("./src/main/wasm/dummy1.opt.wasm"))

    val wasmSource = Source.newBuilder("wasm", ByteSequence.create(wasmBinary), s"main.wasm").build()
    ctx.eval(wasmSource)

    (1 to 10_000).foreach(n => {
      val name = s"dummy$n.wasm"
      val wasmSource = Source.newBuilder("wasm", ByteSequence.create(wasmBinary), name).build()
      ctx.eval(wasmSource)
    })

    System.gc()
    println("done...")
    Thread.sleep(15000)

    (1 to 10_000).foreach(n => {
      assert(ctx.getBindings("wasm").getMember(s"dummy${n}.wasm").getMember("run").execute().asInt() == 1)
    })

    System.gc()
    println("done...")
    Thread.sleep(15000)
}