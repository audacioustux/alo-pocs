/*
 * Copyright (C) 2014-2021 Lightbend Inc. <https://www.lightbend.com>
 */
package bench

import org.graalvm.polyglot._
import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory
import org.openjdk.jmh.annotations._

import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.ActorSystem

object TypedActorBenchmark {
  // Constants because they are used in annotations
  final val threads = 4 // update according to cpu
  final val numMessagesPerActorPair = 10 // messages per actor pair

  final val numActors = 512
  final val totalMessages = numMessagesPerActorPair * numActors / 2
  final val timeout = 100.minutes
}

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
class TypedActorBenchmark {
  import TypedActorBenchmark._
  import TypedBenchmarkActors._

  @Param(Array("50"))
  var tpt = 0

  @Param(Array("50"))
  var batchSize = 0

  @Param(
    Array(
      "akka.dispatch.SingleConsumerOnlyUnboundedMailbox",
      "akka.dispatch.UnboundedMailbox"
    )
  )
  var mailbox = ""

  @Param(
    Array("fjp-dispatcher")
  ) //  @Param(Array("fjp-dispatcher", "affinity-dispatcher"))
  var dispatcher = ""

  implicit var system: ActorSystem[Start] = _

  implicit val askTimeout: akka.util.Timeout = akka.util.Timeout(timeout)

  @Setup(Level.Trial)
  def setup(): Unit = {
    BenchmarkActors.requireRightNumberOfCores(threads)
    system = ActorSystem(
      TypedBenchmarkActors.echoActorsSupervisor(
        numMessagesPerActorPair,
        numActors,
        dispatcher,
        batchSize
      ),
      "TypedActorBenchmark",
      ConfigFactory.parseString(s"""
       akka.actor {

         default-mailbox.mailbox-capacity = 512

         fjp-dispatcher {
           executor = "fork-join-executor"
           fork-join-executor {
             parallelism-min = $threads
             parallelism-factor = 1.0
             parallelism-max = $threads
           }
           throughput = $tpt
           mailbox-type = "$mailbox"
         }
         affinity-dispatcher {
            executor = "affinity-pool-executor"
            affinity-pool-executor {
              parallelism-min = $threads
              parallelism-factor = 1.0
              parallelism-max = $threads
              task-queue-size = 512
              idle-cpu-level = 5
              fair-work-distribution.threshold = 2048
            }
            throughput = $tpt
            mailbox-type = "$mailbox"
         }
       }
      """)
    )
  }

  @TearDown(Level.Trial)
  def shutdown(): Unit = {
    system.terminate()
    Await.ready(system.whenTerminated, 15.seconds)
  }

  @Benchmark
  @OperationsPerInvocation(totalMessages)
  def echo(): Unit = {
    Await.result(system.ask(TypedBenchmarkActors.Start.apply), timeout)
  }

}
