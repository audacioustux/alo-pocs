package bench.CtxPool

import examples.Realworld.*
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.graalvm.polyglot._
import concurrent.duration._
import java.nio.file.{Files, Path}
import org.graalvm.polyglot.io.ByteSequence;

object NPAgent {
  sealed trait Request
  case object Start extends Request
  case object Run extends Request

  sealed trait Response
  case object Ready extends Response
  case object Done extends Response

  val wasmBinary = Files.readAllBytes(
    Path.of(
      "src/main/rust/target/wasm32-wasi/release/rust.opt.wasm"
    )
  );
  val wasmSource =
    Source
      .newBuilder("wasm", ByteSequence.create(wasmBinary), "realworld.wasm")
      .build();

  def apply(
      respondTo: ActorRef[Response],
      numOfTimeScheduleNPA: Int,
      engine: Engine
  ): Behavior[Request] = {
    // val polyCtx = Context.newBuilder().allowAllAccess(true).engine(engine).build()

    val polyCtx = Context
      .newBuilder()
      .allowAllAccess(true)
      .engine(engine)
      .option("wasm.Builtins", "wasi_snapshot_preview1")
      .build()

    Behaviors.setup(context =>
      new NPAgent(context, respondTo, polyCtx, numOfTimeScheduleNPA)
    )
  }
}
class NPAgent(
    context: ActorContext[NPAgent.Request],
    respondTo: ActorRef[NPAgent.Response],
    polyCtx: Context,
    numOfTimeScheduleNPA: Int
) extends AbstractBehavior[NPAgent.Request](context) {
  import NPAgent._

  // val source =
  //   Source
  //     .newBuilder(
  //       "js",
  //       "import { run } from 'src/main/js/realworld.mjs';" + "run",
  //       "realworld.mjs"
  //     )
  //     .build()
  // val execValue = polyCtx.eval(source)
  // execValue.execute()

  // val articles = new ArticleController()

  polyCtx.eval(wasmSource)
  private val wasmMainFn =
    polyCtx
      .getBindings("wasm")
      .getMember("main")
      .getMember("run")

  respondTo ! Ready
  var n = 0

  def onMessage(msg: Request): Behavior[Request] = {
    msg match {
      case Run =>
        wasmMainFn.execute()

        // execValue.execute()

        // articles.create(
        //   Event(
        //     Some("audacioustux"),
        //     Some(
        //       Article(
        //         "testing blabla",
        //         "testing bloom phew phew",
        //         "lorem *ipsum* sit amet dolor am jam kathal",
        //         tagList = Some(Set("test", "test", "bloom", "lorem-ipsum"))
        //       )
        //     )
        //   )
        // )

        n += 1
        if (n < numOfTimeScheduleNPA) {
          context.self ! Run
        } else {
          respondTo ! Done
        }
      // articles.reset()
      case Start =>
        if (numOfTimeScheduleNPA > 0)
          context.self ! Run
        else
          respondTo ! Done
    }
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[Request]] = {
    case PostStop => {
      polyCtx.close()
      this
    }
  }
}

object NPAgentScheduler {
  sealed trait Request
  case object Start extends Request
  private final case class AdaptedNPAgentResponse(response: NPAgent.Response)
      extends Request

  sealed trait Response
  case object Ready extends Response
  case object Done extends Response

  def apply(
      respondTo: ActorRef[Response],
      numOfNPA: Int,
      numOfTimeScheduleNPA: Int,
      engine: Engine
  ): Behavior[Request] =
    Behaviors.setup(context =>
      new NPAgentScheduler(
        context,
        respondTo,
        numOfNPA,
        numOfTimeScheduleNPA,
        engine
      )
    )
}
class NPAgentScheduler(
    context: ActorContext[NPAgentScheduler.Request],
    respondTo: ActorRef[NPAgentScheduler.Response],
    numOfNPA: Int,
    numOfTimeScheduleNPA: Int,
    engine: Engine
) extends AbstractBehavior[NPAgentScheduler.Request](context) {
  import NPAgentScheduler._

  val NPAgentResponseAdapter: ActorRef[NPAgent.Response] =
    context.messageAdapter(rsp => AdaptedNPAgentResponse(rsp))

  val npagents = (1 to numOfNPA).map { n =>
    context.spawn(
      NPAgent(NPAgentResponseAdapter, numOfTimeScheduleNPA, engine),
      "npagent_" + n
    )
  }

  var numOfNPAgentReady = 0
  var numOfNPAgentDone = 0

  def onMessage(msg: Request): Behavior[Request] = {
    msg match {
      case AdaptedNPAgentResponse(rsp) =>
        rsp match {
          case NPAgent.Ready =>
            numOfNPAgentReady += 1
            if (numOfNPAgentReady == numOfNPA)
              respondTo ! Ready
            this
          case NPAgent.Done =>
            numOfNPAgentDone += 1
            if (numOfNPAgentDone == numOfNPA)
              respondTo ! Done
            this
        }
      case Start =>
        npagents.foreach(npa => npa ! NPAgent.Start)
        this
    }
  }
}

object NPAgentSupervisor {
  sealed trait Request
  private final case class AdaptedNPAgentSupResponse(
      response: NPAgentScheduler.Response
  ) extends Request

  def apply(
      respondTo: ActorRef[BenchGuardian.Completed],
      numOfNPA: Int,
      numOfTimeScheduleNPA: Int,
      threads: Int
  ): Behavior[Request] = {
    val engine = Engine
      .newBuilder()
      .option("engine.Mode", "throughput")
      .build()

    Behaviors.setup(context =>
      new NPAgentSupervisor(
        context,
        respondTo,
        numOfNPA,
        numOfTimeScheduleNPA,
        threads,
        engine
      )
    )
  }
}
class NPAgentSupervisor(
    context: ActorContext[NPAgentSupervisor.Request],
    respondTo: ActorRef[BenchGuardian.Completed],
    numOfNPA: Int,
    numOfTimeScheduleNPA: Int,
    threads: Int,
    engine: Engine
) extends AbstractBehavior[NPAgentSupervisor.Request](context) {
  import NPAgentSupervisor._

  val NPAgentSchedulerResponseAdapter: ActorRef[NPAgentScheduler.Response] =
    context.messageAdapter(rsp => AdaptedNPAgentSupResponse(rsp))

  val numOfNPAgentScheduler = threads min numOfNPA

  val NPASupervisors = (1 to numOfNPAgentScheduler).map(n => {
    context.spawn(
      NPAgentScheduler(
        NPAgentSchedulerResponseAdapter,
        numOfNPA / numOfNPAgentScheduler,
        numOfTimeScheduleNPA,
        engine
      ),
      "npa_sup_" + n
    )
  })
  var numOfNPAgentSchedulerReady = 0
  var numOfNPAgentSchedulerDone = 0

  var startNanoTime: Long = _

  def onMessage(msg: Request): Behavior[Request] = {
    msg match {
      case AdaptedNPAgentSupResponse(rsp) =>
        rsp match {
          case NPAgentScheduler.Ready =>
            numOfNPAgentSchedulerReady += 1
            if (numOfNPAgentSchedulerReady == numOfNPAgentScheduler) {
              startNanoTime = System.nanoTime()
              NPASupervisors.foreach(npa => npa ! NPAgentScheduler.Start)
            }
          case NPAgentScheduler.Done => {
            numOfNPAgentSchedulerDone += 1
            if (numOfNPAgentSchedulerDone == numOfNPAgentScheduler) {
              BenchGuardian.printProgress(
                numOfTimeScheduleNPA,
                numOfNPA,
                startNanoTime
              )
              respondTo ! BenchGuardian.Completed()
              return Behaviors.stopped
            }
          }
        }
    }
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[Request]] = {
    case PostStop => {
      engine.close()
      this
    }
  }
}

object BenchGuardian {
  case class Completed()
  final case class Start(respondTo: ActorRef[Completed])

  def apply(
      numOfNPA: Int,
      numOfTimeScheduleNPA: Int,
      threads: Int
  ): Behavior[Start] =
    Behaviors.setup(context =>
      new BenchGuardian(context, numOfNPA, numOfTimeScheduleNPA, threads)
    )

  def printProgress(
      numOfTimeScheduleNPA: Long,
      numOfNPA: Int,
      startNanoTime: Long
  ) = {
    val durationMicros = (System.nanoTime() - startNanoTime) / 1000
    val totalNumOfTimeScheduledNPA = numOfTimeScheduleNPA * numOfNPA

    println(
      f"  $totalNumOfTimeScheduledNPA times executed by $numOfNPA NPAgent took ${durationMicros / 1000} ms, " +
        f"${totalNumOfTimeScheduledNPA.toDouble / durationMicros}%,.2f M msg/s"
    )
  }
}

class BenchGuardian(
    context: ActorContext[BenchGuardian.Start],
    numOfNPA: Int,
    numOfTimeScheduleNPA: Int,
    threads: Int
) extends AbstractBehavior[BenchGuardian.Start](context) {
  import BenchGuardian._

  override def onMessage(msg: Start): Behavior[Start] =
    context.spawnAnonymous(
      NPAgentSupervisor(msg.respondTo, numOfNPA, numOfTimeScheduleNPA, threads)
    )
    this
}
