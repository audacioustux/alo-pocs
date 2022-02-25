package bench.Scala

import akka.Done
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.actor.typed.scaladsl.AskPattern.*
import akka.util.Timeout
import org.graalvm.polyglot.*
import org.graalvm.polyglot.io.ByteSequence

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

import example.Articles

object Agent {
  sealed trait Event
  case class Ready(replyTo: ActorRef[Request]) extends Event
  case object Completed                        extends Event
  sealed trait Request
  final case class Execute(times: Int) extends Request { assert(times > 0) }
  private case object Execute          extends Request

  def apply(
      replyTo: ActorRef[Event],
      source: Articles.type
  ): Behavior[Request] =
    Behaviors.setup(ctx =>
      replyTo ! Ready(ctx.self)

      val articles: Articles = source.apply()
      new Agent(ctx, replyTo, articles.run).ready
    )
}
class Agent(
    ctx: ActorContext[Agent.Request],
    replyTo: ActorRef[Agent.Event],
    executable: () => Unit
) {
  import Agent.*

  private def ready: Behavior[Request] =
    Behaviors.receiveMessagePartial { case Execute(times) =>
      ctx.self ! Execute
      active(times)
    }

  private def active(left: Int): Behavior[Request] =
    Behaviors.receiveMessagePartial { case Execute =>
      if (left > 0)
        executable()
        ctx.self ! Execute
        active(left - 1)
      else
        replyTo ! Completed; ready
    }
}

object AgentBench {

  sealed trait Request
  case object Shutdown                                         extends Request
  final case class Execute(times: Int)                         extends Request { assert(times > 0) }
  private final case class AdaptedAgentEvent(res: Agent.Event) extends Request

  def printProgress(
      numOfAgent: Int,
      totalTimesExecuted: Int,
      startNanoTime: Long
  ): Unit = {
    val durationMicros = (System.nanoTime() - startNanoTime) / 1000

    println(
      f"  $totalTimesExecuted times executed by $numOfAgent Agent took ${durationMicros / 1000} ms, " +
        f"${totalTimesExecuted.toDouble / durationMicros}%,.2f M msg/s"
    )
  }

  def apply(
      source: Articles.type,
      numOfAgent: Int
  ): Behavior[Request] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.setup[Request] { ctx =>
        val agentEventAdapter: ActorRef[Agent.Event] =
          ctx.messageAdapter(AdaptedAgentEvent.apply)

        (1 to numOfAgent).foreach(n => ctx.spawnAnonymous(Agent(agentEventAdapter, source)))

        new AgentBench(ctx, buffer, numOfAgent).starting
      }
    }

  private def bench(
      source: Articles.type,
      numOfAgent: Int,
      executeTimes: Int,
      iterateTimes: Int
  ) = {
    if executeTimes % numOfAgent != 0 then
      throw Error("executeTimes should be evenly distributed among numOfAgent")

    val system: ActorSystem[Request] =
      ActorSystem(AgentBench(source, numOfAgent), "bench")

    (1 to iterateTimes).foreach(n => system ! Execute(executeTimes))

    system ! Shutdown

    val timeout               = 30.minutes
    given askTimeout: Timeout = Timeout(timeout)

    Await.ready(system.whenTerminated, timeout)
  }

  def main(args: Array[String]): Unit = {
    System.gc()
    Thread.sleep(5000)
    bench(
      Articles,
      4,
      10_000_000,
      10
    )
    System.gc()
    Thread.sleep(5000)
    bench(
      Articles,
      100_000,
      10_000_000,
      10
    )
  }
}
class AgentBench(
    ctx: ActorContext[AgentBench.Request],
    buffer: StashBuffer[AgentBench.Request],
    numOfAgent: Int
) {
  import AgentBench.*

  private val readyAgents = ArrayBuffer[ActorRef[Agent.Request]]()
  private def starting: Behavior[Request] =
    Behaviors.receiveMessage {
      case AdaptedAgentEvent(Agent.Ready(ref)) =>
        readyAgents += ref
        if readyAgents.length == numOfAgent then buffer.unstashAll(ready)
        else Behaviors.same
      case other =>
        buffer.stash(other)
        Behaviors.same
    }

  private def ready: Behavior[Request] =
    Behaviors.receiveMessagePartial {
      case Execute(times) =>
        readyAgents.foreach(agent => agent ! new Agent.Execute(times / numOfAgent))
        waitForCompletion(System.nanoTime(), times)
      case Shutdown => Behaviors.stopped
    }

  private def waitForCompletion(
      startedAt: Long,
      totalTimesExecute: Int
  ): Behavior[Request] =
    var completedAgents = 0
    Behaviors.receiveMessage {
      case AdaptedAgentEvent(Agent.Completed) =>
        completedAgents += 1
        if completedAgents == numOfAgent then
          printProgress(numOfAgent, totalTimesExecute, startedAt)
          buffer.unstashAll(ready)
        else Behaviors.same
      case other =>
        buffer.stash(other)
        Behaviors.same
    }
}
