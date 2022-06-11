package bench

import java.nio.file.{Files, Path}
import org.graalvm.polyglot.*
import org.graalvm.polyglot.io.ByteSequence
import org.junit.Test
import org.junit.Assert._

class MultiWasmSourceCtxSharing {
  val ctx: Context = Context.create()

  @Test
  def initMultiSourceInSingleCtx(): Unit =
    val wasmBinary1 = Files.readAllBytes(Path.of("./src/main/wasm/dummy1.opt.wasm"))
    val wasmSource1 = Source.newBuilder("wasm", ByteSequence.create(wasmBinary1), "dummy1.wasm").build()
    ctx.eval(wasmSource1)

    val wasmBinary2 = Files.readAllBytes(Path.of("./src/main/wasm/dummy2.opt.wasm"))
    val wasmSource2 = Source.newBuilder("wasm", ByteSequence.create(wasmBinary2), "dummy2.wasm").build()
    ctx.eval(wasmSource2)

    val wasmBinary3 = Files.readAllBytes(Path.of("./src/main/wasm/dummy3.opt.wasm"))
    val wasmSource3 = Source.newBuilder("wasm", ByteSequence.create(wasmBinary3), "dummy3.wasm").build()
    ctx.eval(wasmSource3)

    assertEquals(ctx.getBindings("wasm").getMember("main").getMember("run").execute().asInt(), 1)
    assertEquals(ctx.getBindings("wasm").getMember("dummy2.wasm").getMember("run").execute().asInt(), 2)
    assertEquals(ctx.getBindings("wasm").getMember("dummy3.wasm").getMember("run3").execute().asInt(), 3)
}