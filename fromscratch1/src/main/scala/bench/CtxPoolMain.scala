package bench.CtxPool

import scala.concurrent.duration._
import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import com.typesafe.config.ConfigFactory
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.actor.typed._
import akka.Done

object CtxPoolMain {
  final val threads = Runtime.getRuntime.availableProcessors
  final val numOfNPA = 100 * 1000
  final val numOfTimeScheduleNPA = 20
  final val timeout = 60.minutes

  def main(args: Array[String]): Unit = {
    (1 to 1).foreach { n =>
      val system = new CtxPoolMain
      system.setup()
      system.echo()
      system.shutdown()
    }
  }
}
class CtxPoolMain {
  import CtxPoolMain.*

  var tpt = numOfTimeScheduleNPA
  var batchSize = numOfTimeScheduleNPA

  var mailbox = "akka.dispatch.SingleConsumerOnlyUnboundedMailbox"

  var dispatcher = "fjp-dispatcher"

  implicit var system: ActorSystem[BenchGuardian.Start] = _
  implicit val askTimeout: akka.util.Timeout = akka.util.Timeout(timeout)

  def setup(): Unit = {
    system = ActorSystem(
      BenchGuardian(numOfNPA, numOfTimeScheduleNPA, threads),
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
    Await.ready(system.whenTerminated, 5.minutes)
  }

  def echo(): Unit = {
    Await.result(
      system.ask(BenchGuardian.Start.apply),
      timeout
    )
  }
}
