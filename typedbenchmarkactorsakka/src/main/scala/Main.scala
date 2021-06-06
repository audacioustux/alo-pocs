import java.io.PrintWriter
import org.graalvm.polyglot.*

@main def main() =
  Thread.sleep(5000)
  val polyCtx = Context
    .newBuilder()
    .allowAllAccess(true)
    .option("engine.Mode", "throughput")
    .build()
  polyCtx.eval("js", "1+Math.random()")
  val value =
    polyCtx.eval(
      Source
        .newBuilder(
          "js",
          "(1+Math.random()).toString()",
          "dummymodule"
        )
        .build()
    )
  new PrintWriter("somelog") { write(value.asString()); close }
  polyCtx.close()
