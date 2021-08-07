/*
 * Copyright (C) 2014-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package bench

import java.util.concurrent.TimeUnit

import scala.concurrent.Await
import scala.concurrent.duration._

import com.typesafe.config.ConfigFactory

import akka.actor.typed.scaladsl.AskPattern._

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.Props
import akka.actor.typed.ActorSystem

object TypedActorBenchmark {
  // Constants because they are used in annotations
  final val threads = 4 // update according to cpu
  final val numMessagesPerActorPair = 40000000 // messages per actor pair

  final val numActors = 8
  final val totalMessages = numMessagesPerActorPair * (numActors / 2)
  final val timeout = 100.minutes

  @main def start(): Unit = {
    System.out.println(ProcessHandle.current().pid())
    Thread.sleep(20000)
    (1 to 10).map { n =>
      System.out.println(n)
      val system = new TypedActorBenchmark
      system.setup()
      system.echo()
      System.gc()
      Thread.sleep(5000)
      system.shutdown()
      System.gc()
      Thread.sleep(2000)
    }
  }
}

class TypedActorBenchmark {
  import TypedActorBenchmark._
  import TypedBenchmarkActors._

  var tpt = 50

  var batchSize = 50

  var mailbox = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"

  var dispatcher = "fjp-dispatcher"

  implicit var system: ActorSystem[Start] = _

  implicit val askTimeout: akka.util.Timeout = akka.util.Timeout(timeout)

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

  def shutdown(): Unit = {
    system.terminate()
    Await.ready(system.whenTerminated, 2.minutes)
  }

  def echo(): Unit = {
    Await.result(
      system.ask(TypedBenchmarkActors.Start.apply),
      timeout
    )
  }

}
