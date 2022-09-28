package bench

import org.graalvm.polyglot._
import org.graalvm.polyglot.io.ByteSequence
import scala.util.Random
import java.nio.file.{Files, Path}

object jsMemoryMeasure {

  def main(args: Array[String]): Unit =
    Thread.sleep(10000)
    val engine = Engine.create()
    val ctx: Context = Context.newBuilder().engine(engine).build()

    val jsSource = Source.newBuilder("js", "() => null", s"main.mjs").build()
    ctx.eval(jsSource)

    (1 to 10_000).foreach(n => {
      val name = s"dummy$n.mjs"
      val jsSource = Source.newBuilder("js", "function run() { return null }; export default run", name).build()
      ctx.eval(jsSource)
    })

    System.gc()
    println("done...")
    Thread.sleep(15000)

    (1 to 1).foreach(n => {
      print(ctx.getBindings("js").getMember("global"))
    })

    System.gc()
    println("done...")
    Thread.sleep(15000)
}