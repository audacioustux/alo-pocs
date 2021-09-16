package bench.CtxPool

import akka.actor.typed._
import akka.actor.typed.scaladsl._
import org.graalvm.polyglot._

object NPAgent {
  sealed trait Message

  def apply(
      respondTo: ActorRef[Unit],
      engine: Engine
  ): Behavior[Message] =
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
class NPAgent(
    context: ActorContext[NPAgent.Message],
    respondTo: ActorRef[Unit],
    source: Source,
    polyCtx: Context
) extends AbstractBehavior[NPAgent.Message](context) {
  import NPAgent._

  private val fnMain = polyCtx.eval(source)

  def onMessage(msg: Message): Behavior[Message] = {
    fnMain.execute()
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[Message]] = {
    case PostStop =>
      polyCtx.close()
      this
  }
}

object NPAgentSupervisor {
  def apply(numberOfNPA: Int, engine: Engine): Behavior[Unit] =
    Behaviors.setup(context =>
      new NPAgentSupervisor(context, numberOfNPA, engine)
    )
}
class NPAgentSupervisor(
    context: ActorContext[Unit],
    numberOfNPA: Int,
    engine: Engine
) extends AbstractBehavior[Unit](context) {
  val npagents = (1 to numberOfNPA).map { n =>
    context.spawn(NPAgent(context.self, engine), "npagent_" + n)
  }
  def onMessage(msg: Unit): Behavior[Unit] = { this }
}

object NPABenchSessionActor {
  def apply(numberOfNPA: Int): Behavior[Unit] =
    Behaviors.setup(context => new NPABenchSessionActor(context, numberOfNPA))
}
class NPABenchSessionActor(context: ActorContext[Unit], numberOfNPA: Int)
    extends AbstractBehavior[Unit](context) {
  def onMessage(msg: Unit) = { this }
}
