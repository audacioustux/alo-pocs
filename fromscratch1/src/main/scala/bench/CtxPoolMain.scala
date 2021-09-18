package bench.CtxPool

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.graalvm.polyglot._
import concurrent.duration._

object NPAgent {
  sealed trait Request
  case object Start extends Request

  sealed trait Response
  case object Ready extends Response

  def apply(
      respondTo: ActorRef[Response],
      engine: Engine
  ): Behavior[Request] = {
    val polyCtx =
      Context.newBuilder().allowAllAccess(true).engine(engine).build()

    val source =
      Source
        .newBuilder(
          "js",
          "import { run } from 'src/main/js/realworld.mjs';" + "run",
          "realworld.mjs"
        )
        .build()

    Behaviors.setup(context => new NPAgent(context, respondTo, source, polyCtx))
  }
}
class NPAgent(
    context: ActorContext[NPAgent.Request],
    respondTo: ActorRef[NPAgent.Response],
    source: Source,
    polyCtx: Context
) extends AbstractBehavior[NPAgent.Request](context) {
  import NPAgent._

  private val fnMain = polyCtx.eval(source)
  respondTo ! Ready

  def onMessage(msg: Request): Behavior[Request] = {
    fnMain.execute()

    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[Request]] = {
    case PostStop =>
      polyCtx.close()
      this
  }
}

object NPAgentSupervisor {
  sealed trait Request
  case object Start extends Request
  private final case class WrappedNPAgentResponse(response: NPAgent.Response)
      extends Request

  sealed trait Response
  case object Ready extends Response

  def apply(
      respondTo: ActorRef[Response],
      numberOfNPA: Int,
      engine: Engine
  ): Behavior[Request] =
    Behaviors.setup(context =>
      new NPAgentSupervisor(context, respondTo, numberOfNPA, engine)
    )
}
class NPAgentSupervisor(
    context: ActorContext[NPAgentSupervisor.Request],
    respondTo: ActorRef[NPAgentSupervisor.Response],
    numberOfNPA: Int,
    engine: Engine
) extends AbstractBehavior[NPAgentSupervisor.Request](context) {
  import NPAgentSupervisor._

  val NPAgentResponseAdapter: ActorRef[NPAgent.Response] =
    context.messageAdapter(rsp => WrappedNPAgentResponse(rsp))

  val npagents = (1 to numberOfNPA).map { n =>
    context.spawn(NPAgent(NPAgentResponseAdapter, engine), "npagent_" + n)
  }

  var numberOfNPAgentReady = 0

  def onMessage(msg: Request): Behavior[Request] = {
    msg match {
      case WrappedNPAgentResponse(rsp) =>
        rsp match {
          case NPAgent.Ready =>
            numberOfNPAgentReady += 1
            if (numberOfNPAgentReady == numberOfNPA)
              respondTo ! Ready
            // TODO: should change behaviour here
            this
        }
      case Start =>
        npagents.foreach(npa => npa ! NPAgent.Start)
        this
    }
  }
}

object NPABenchSupervisor {
  sealed trait Request
  case object Start extends Request
  private final case class WrappedNPAgentSupResponse(
      response: NPAgentSupervisor.Response
  ) extends Request

  def apply(numberOfNPA: Int, threads: Int): Behavior[Request] = {
    val engine = Engine
      .newBuilder()
      .option("engine.Mode", "throughput")
      .build()

    Behaviors.setup(context =>
      new NPABenchSupervisor(context, numberOfNPA, threads, engine)
    )
  }
}
class NPABenchSupervisor(
    context: ActorContext[NPABenchSupervisor.Request],
    numberOfNPA: Int,
    threads: Int,
    engine: Engine
) extends AbstractBehavior[NPABenchSupervisor.Request](context) {
  import NPABenchSupervisor._

  val NPAgentSupResponseAdapter: ActorRef[NPAgentSupervisor.Response] =
    context.messageAdapter(rsp => WrappedNPAgentSupResponse(rsp))

  val NPASupervisors = (1 to threads).map(n =>
    context.spawn(
      NPAgentSupervisor(NPAgentSupResponseAdapter, numberOfNPA, engine),
      "npa_sup_" + n
    )
  )

  def onMessage(msg: Request): Behavior[Request] = {
    msg match {
      case WrappedNPAgentSupResponse(rsp) =>
        rsp match {
          case NPAgentSupervisor.Ready =>
            numberOfNPAgentSupReady += 1
            if (numberOfNPAgentSupReady == threads)
              respondTo ! Ready
            // TODO: should change behaviour here
            this
        }
      case Start =>
        NPASupervisors.foreach(npa => npa ! NPAgentSupervisor.Start)
        respondTo ! Ready
        this
    }
  }
}
