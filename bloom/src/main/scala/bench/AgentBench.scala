package bench

import akka.Done
import akka.actor.typed.*
import akka.actor.typed.scaladsl.*
import akka.actor.typed.scaladsl.AskPattern.*
import akka.util.Timeout
import bench.Task
import org.graalvm.polyglot.*
import org.graalvm.polyglot.io.ByteSequence

import java.io.File
import java.nio.file.{Files, Path}
import scala.collection.mutable
import scala.collection.mutable.ArrayBuffer
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.concurrent.duration.*
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
// import example.{Article, Articles, Event}

object Agent {
  private val engine = Engine
    .newBuilder()
    .option("engine.Mode", "throughput")
    .build()
  private val polyCtxBuilder = Context
    .newBuilder()
    .allowAllAccess(true)
    .engine(engine)

//  def apply[T](
//      replyTo: ActorRef[Event],
//      task: Task[T]
//  ): Behavior[Request] =
//    Behaviors.setup(ctx =>
//      if task.getLanguage.equals("python") then
//        polyCtxBuilder.option(
//          "python.Executable",
//          "/Users/tanjimhossain/Bytes/poc-wormhole/bloom/env/bin/graalpython"
//        )
//      if task.getLanguage.equals("wasm") then
//        polyCtxBuilder.option("wasm.Builtins", "wasi_snapshot_preview1")
//
//      val polyCtx = polyCtxBuilder.build()
//
//      val evalResult = polyCtx.eval(task)
//      val executable =
//        if task.getLanguage.equals("wasm") then
//          polyCtx
//            .getBindings("wasm")
//            .getMember("main")
//            .getMember("run")
//        else evalResult
//
//        replyTo ! Ready(ctx.self)
//      new Agent(ctx, replyTo, executable).ready
//    )

  def apply(
      replyTo: ActorRef[Event],
      task: Task
  ): Behavior[Request] =
    Behaviors.setup(ctx =>
      task.execute()
      Behaviors.empty
    )

  sealed trait Event

  sealed trait Request

  case class Ready(replyTo: ActorRef[Request]) extends Event

  final case class Execute(times: Int) extends Request {
    assert(times > 0)
  }

  case object Completed extends Event

  private case object Execute extends Request
}

class Agent(
    ctx: ActorContext[Agent.Request],
    replyTo: ActorRef[Agent.Event],
    executable: Value
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
        executable.execute()
        ctx.self ! Execute
        active(left - 1)
      else
        replyTo ! Completed
        ready
    }
}

object AgentBench {
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

  def main(args: Array[String]): Unit = {
    val tasks = Array(
      // Source.newBuilder("python", "lambda : 1 + 2", "dummy.py").build()
      // Source
      //   .newBuilder(
      //     "python",
      //     new File("src/main/python/article.py")
      //   )
      //   .build(),
      // Source
      //   .newBuilder(
      //     "js",
      //     "() => 1 + 2",
      //     "dummy.mjs"
      //   )
      //   .build(),
      // Source
      //   .newBuilder(
      //     "js",
      //     "import { run } from '../fromscratch1/src/main/js/realworld.mjs';" + "run",
      //     "article.mjs"
      //   )
      //   .build(),
      Task(
        Source
          .newBuilder(
            "wasm",
            ByteSequence
              .create(
                Files.readAllBytes(
                  Path.of(
                    "../fromscratch1/src/main/rust/target/wasm32-wasi/release/rust.opt.wasm"
                  )
                )
              ),
            "article.wasm"
          )
          .build()
      )
      // Task(Articles)
    )

    tasks.foreach(task => bench(task, 4, 100_000, 10))

  }

  private def bench(
      task: Task,
      numOfAgent: Int,
      executeTimes: Int,
      iterateTimes: Int
  ) = {
    System.gc()

    val system: ActorSystem[Request] =
      ActorSystem(AgentBench(task, numOfAgent), "bench")

    (1 to iterateTimes).foreach(n => system ! Execute(executeTimes))

    system ! Shutdown

    val timeout = 30.minutes

    given askTimeout: Timeout = Timeout(timeout)

    Await.ready(system.whenTerminated, timeout)
  }

  def apply(
      task: Task,
      numOfAgent: Int
  ): Behavior[Request] =
    Behaviors.withStash(100) { buffer =>
      Behaviors.setup[Request] { ctx =>
        val agentEventAdapter: ActorRef[Agent.Event] =
          ctx.messageAdapter(AdaptedAgentEvent.apply)

        (1 to numOfAgent).foreach(n => ctx.spawnAnonymous(Agent(agentEventAdapter, task)))

        new AgentBench(ctx, buffer, numOfAgent).starting
      }
    }

  sealed trait Request

  final case class Execute(times: Int) extends Request {
    assert(times > 0)
  }

  private final case class AdaptedAgentEvent(res: Agent.Event) extends Request

  case object Shutdown extends Request
}

class AgentBench(
    ctx: ActorContext[AgentBench.Request],
    buffer: StashBuffer[AgentBench.Request],
    numOfAgent: Int
) {

  import AgentBench.*

  var readyAgents: mutable.Seq[ActorRef[Agent.Request]] = ArrayBuffer[ActorRef[Agent.Request]]()

  private def starting: Behavior[Request] =
    Behaviors.receiveMessage {
      case AdaptedAgentEvent(Agent.Ready(ref)) =>
        readyAgents :+= ref
        if readyAgents.length == numOfAgent then buffer.unstashAll(ready)
        else Behaviors.same
      case other => buffer.stash(other); Behaviors.same
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
      case other => buffer.stash(other); Behaviors.same
    }
}
