package bench.Polyglot

import scala.language.postfixOps
import org.openjdk.jmh.annotations._
import java.util.concurrent.TimeUnit
import org.graalvm.polyglot._
import AgentBench._

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
class AgentBenchJMH {
  val sources = Seq(
    Source
      .newBuilder(
        "js",
        "import { run } from '../fromscratch1/src/main/js/realworld.mjs';" + "run",
        "article.mjs"
      )
      .build()
  )
  @Benchmark
  @OperationsPerInvocation(10_000_000)
  def agentBench(): Unit = {
    sources.foreach(source =>
      bench(
        source,
        100_000,
        10_000_000,
        1
      )
    )
  }
}
