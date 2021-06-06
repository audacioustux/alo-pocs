package org.openjdk.jmh.samples
import org.graalvm.polyglot.*
import java.util.concurrent.TimeUnit
import org.openjdk.jmh.annotations._

@State(Scope.Benchmark)
@BenchmarkMode(Array(Mode.Throughput))
@Fork(1)
@Threads(1)
@Warmup(iterations = 10, time = 5, timeUnit = TimeUnit.SECONDS, batchSize = 1)
@Measurement(
  iterations = 10,
  time = 15,
  timeUnit = TimeUnit.SECONDS,
  batchSize = 1
)
class PolyCtxBench {

  @Benchmark
  def wellHelloThere(): Unit = {
    val polyCtx = Context
      .newBuilder()
      .allowAllAccess(true)
      .option("engine.Mode", "throughput")
      .build()
    polyCtx.eval("js", "")
    val value =
      polyCtx.eval(
        Source
          .newBuilder(
            "js",
            "Math.random()",
            "dummymodule"
          )
          .build()
      )
    // new PrintWriter("somelog") { write(value.asString()); close }
    polyCtx.close()
  }

}
