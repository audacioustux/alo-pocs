package bench

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import com.typesafe.config.ConfigFactory
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.actor.typed._
import akka.Done
import CtxReusePerActorPair._

object CtxReusePerActorPairMain {
  final val threads = Runtime.getRuntime.availableProcessors
  final val numMessagesPerActorPair = 2
  final val numActors = 2
  final val totalMessages = numMessagesPerActorPair * (numActors / 2)
  final val timeout = 60.minutes

  @main def start(): Unit = {
    System.out.println(ProcessHandle.current().pid())
    Thread.sleep(20000)
    (1 to 1).map { n =>
      System.out.println(n)
      val system = new CtxReusePerActorPairMain
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
class CtxReusePerActorPairMain {
  import CtxReusePerActorPairMain.*

  var tpt = 1
  var batchSize = 1

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
    Await.ready(system.whenTerminated, 5.minutes)
  }

  def echo(): Unit = {
    Await.result(
      system.ask(EchoActorSupervisor.Start.apply),
      timeout
    )
  }
}
