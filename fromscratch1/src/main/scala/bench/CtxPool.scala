package bench.CtxPool

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.graalvm.polyglot._
import concurrent.duration._

object NPAgent {
  sealed trait Request
  case object Start extends Request
  case object Run extends Request

  sealed trait Response
  case object Ready extends Response
  case object Done extends Response

  def apply(
      respondTo: ActorRef[Response],
      numOfTimeScheduleNPA: Int,
      engine: Engine
  ): Behavior[Request] = {
    val polyCtx = Context.newBuilder().allowAllAccess(true).engine(engine).build()

    Behaviors.setup(context => new NPAgent(context, respondTo, polyCtx, numOfTimeScheduleNPA))
  }
}
class NPAgent(
    context: ActorContext[NPAgent.Request],
    respondTo: ActorRef[NPAgent.Response],
    polyCtx: Context,
    numOfTimeScheduleNPA: Int
) extends AbstractBehavior[NPAgent.Request](context) {
  import NPAgent._

  val source =
    Source
      .newBuilder(
        "js",
        "import { run } from 'src/main/js/realworld.mjs';" + "run",
        "realworld.mjs"
      )
      .build()
  val execValue = polyCtx.eval(source)
  execValue.execute()

  respondTo ! Ready

  var n = 0

  def onMessage(msg: Request): Behavior[Request] = {
    msg match {
      case Run =>
        execValue.execute()
        n += 1
        if (n < numOfTimeScheduleNPA) {
          context.self ! Run
        } else {
          respondTo ! Done
        }
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
  private final case class AdaptedNPAgentResponse(response: NPAgent.Response) extends Request

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
      new NPAgentScheduler(context, respondTo, numOfNPA, numOfTimeScheduleNPA, engine)
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
    context.spawn(NPAgent(NPAgentResponseAdapter, numOfTimeScheduleNPA, engine), "npagent_" + n)
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
      new NPAgentSupervisor(context, respondTo, numOfNPA, numOfTimeScheduleNPA, threads, engine)
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

  val NPASupervisors = (1 to (threads min numOfNPA)).map(n => {
    context.spawn(
      NPAgentScheduler(
        NPAgentSchedulerResponseAdapter,
        numOfNPA / threads,
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
            if (numOfNPAgentSchedulerReady == threads) {
              startNanoTime = System.nanoTime()
              NPASupervisors.foreach(npa => npa ! NPAgentScheduler.Start)
            }
          case NPAgentScheduler.Done => {
            numOfNPAgentSchedulerDone += 1
            if (numOfNPAgentSchedulerDone == threads) {
              BenchGuardian.printProgress(numOfTimeScheduleNPA, numOfNPA, startNanoTime)
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

  def apply(numOfNPA: Int, numOfTimeScheduleNPA: Int, threads: Int): Behavior[Start] =
    Behaviors.setup(context => new BenchGuardian(context, numOfNPA, numOfTimeScheduleNPA, threads))

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
