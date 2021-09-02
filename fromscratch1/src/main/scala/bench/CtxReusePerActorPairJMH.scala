package bench

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import com.typesafe.config.ConfigFactory
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.actor.typed._
import akka.Done
import org.openjdk.jmh.annotations._
import CtxReusePerActorPair._

object CtxReusePerActorPairJMH {
  final val threads = Runtime.getRuntime.availableProcessors
  final val numMessagesPerActorPair = 35000000
  final val numActors = 8
  final val totalMessages = numMessagesPerActorPair * (numActors / 2)
  final val timeout = 5.minutes

  @main def start(): Unit = {
    System.out.println(ProcessHandle.current().pid())
    (1 to 10).map { n =>
      System.out.println(n)
      val system = new CtxReusePerActorPairJMH
      system.setup()
      system.echo()
      system.shutdown()
    }
  }
}
class CtxReusePerActorPairJMH {
  import CtxReusePerActorPairJMH.*

  var tpt = 50
  var batchSize = 50
  var mailbox = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"
  var dispatcher = "fjp-dispatcher"

  implicit var system: ActorSystem[EchoActorSupervisor.Start] = _
  implicit val askTimeout: akka.util.Timeout = akka.util.Timeout(timeout)

  def setup(): Unit = {
    system = ActorSystem(
      EchoActorSupervisor(
        EchoActorSupervisorAttrs(
          numMessagesPerActorPair,
          numActors,
          dispatcher,
          batchSize
        )
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
      system.ask(EchoActorSupervisor.Start.apply),
      timeout
    )
  }
}
