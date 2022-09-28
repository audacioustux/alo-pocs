package bench

import org.graalvm.polyglot._
import org.graalvm.polyglot.io.ByteSequence
import scala.util.Random
import java.nio.file.{Files, Path}

object wasmMemoryMeasure {
  def main(args: Array[String]): Unit =
    Thread.sleep(10000)
    val engine = Engine.create()
    val wasmBinary = Files.readAllBytes(Path.of("./src/main/wasm/hello-world.wasm"))

    val ctxs = (1 to 1).map(n =>
      Context.newBuilder()
        .engine(engine)
        .option("wasm.Builtins", "wasi_snapshot_preview1").allowAllAccess(true).build()
    )
    println("done...")
    System.gc()
    Thread.sleep(5000)
    ctxs.zipWithIndex.foreach((ctx, n) => {
      val name = s"hello-world_$n.wasm"
      //      val name = s"dummy_$n.wasm"
      val wasmSource = Source.newBuilder("wasm", ByteSequence.create(wasmBinary), name).build()
      ctx.eval(wasmSource)
    })
    println("done...")
    System.gc()
    Thread.sleep(5000)
    ctxs.zipWithIndex.foreach((ctx, _) => {
      ctx.getBindings("wasm").getMember(s"main").getMember("entry").execute(0, 0)
    })

    // System.gc()
    //    println("done...")
    // Thread.sleep(5000)

    // (1 to 100).foreach(n => {
    //   print(n)
    //   //      print(ctx.getBindings("wasm").getMember(s"hello-world$n.wasm").getMember("_start").execute().asInt())
    //   print(ctx.getBindings("wasm").getMember(s"hello-world$n.wasm").getMember("_start").executeVoid())
    // })

    System.gc()
    println("done...")
    Thread.sleep(5000)
    ()
}