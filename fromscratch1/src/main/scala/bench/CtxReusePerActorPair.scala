package bench.CtxReusePerActorPair

import java.util.concurrent.TimeUnit
import scala.concurrent.Await
import com.typesafe.config.ConfigFactory
import akka.actor.typed.scaladsl.AskPattern._
import akka.actor.typed.scaladsl._
import akka.actor.typed._
import akka.Done

import org.graalvm.polyglot._
import org.graalvm.polyglot.io.ByteSequence;
import java.nio.file.{Files, Path}

sealed trait Command
case object Message extends Command

object EchoActor {
  // private val realworldJsSrc =
  //   "import { run } from 'src/main/js/realworld.mjs';" + "run"
  // private val jsSource =
  //   Source
  //     .newBuilder(
  //       "js",
  //       realworldJsSrc,
  //       "realworld.mjs"
  //     )
  //     .build()

  // private val floydJsSrc =
  //   "import { floyd } from 'src/main/js/floyd.mjs';" + "floyd"
  // private val jsSource =
  //   Source
  //     .newBuilder(
  //       "js",
  //       floydJsSrc,
  //       "floyd.mjs"
  //     )
  //     .build()

  // private val dummyJsSrc =
  //   "import { dummyFn } from 'src/main/js/dummy.mjs';" + "dummyFn"
  // private val jsSource =
  //   Source
  //     .newBuilder(
  //       "js",
  //       dummyJsSrc,
  //       "dummy.mjs"
  //     )
  //     .build()

  val wasmBinary = Files.readAllBytes(
    Path.of(
      "src/main/wasm/realworld/target/wasm32-wasi/release/realworld.opt.wasm"
    )
  );
  val wasmSource =
    Source.newBuilder("wasm", ByteSequence.create(wasmBinary), "floyd").build();

  def apply(respondTo: ActorRef[Command], engine: Engine): Behavior[Command] =
    Behaviors.setup(context => new EchoActor(context, respondTo, engine))
}
class EchoActor(
    context: ActorContext[Command],
    respondTo: ActorRef[Command],
    engine: Engine
) extends AbstractBehavior[Command](context) {
  import EchoActor._

  private val polyCtx = Context
    .newBuilder()
    .allowAllAccess(true)
    .engine(engine)
    .option("wasm.Builtins", "wasi_snapshot_preview1")
    .build()

  polyCtx.eval(wasmSource)
  private val wasmMainFn =
    polyCtx
      .getBindings("wasm")
      .getMember("main")
      .getMember("floyd")
  // private val jsMainFn = polyCtx.eval(jsSource)

  override def onMessage(msg: Command): Behavior[Command] = {
    wasmMainFn.execute()
    // jsMainFn.execute()
    respondTo ! Message
    this
  }

  override def onSignal: PartialFunction[Signal, Behavior[Command]] = {
    case PostStop =>
      polyCtx.close()
      this
  }
}

case class EchoSenderActorAttrs(
    onDone: ActorRef[Done],
    messagesPerPair: Int,
    batchSize: Int,
    childProps: Props,
    engine: Engine
)
object EchoSenderActor {
  def apply(attrs: EchoSenderActorAttrs): Behavior[Command] =
    Behaviors.setup(context => new EchoSenderActor(context, attrs))
}
class EchoSenderActor(
    context: ActorContext[Command],
    attrs: EchoSenderActorAttrs
) extends AbstractBehavior[Command](context) {
  import attrs.*

  val echo = context.spawn(
    EchoActor(context.self, engine),
    "echo",
    childProps
  )

  var left = messagesPerPair / 2
  var batch = 0

  def sendBatch(): Boolean = {
    if (left > 0) {
      var i = 0
      while (i < batchSize) {
        echo ! Message
        i += 1
      }
      left -= batchSize
      batch = batchSize
      true
    } else {
      false
    }
  }

  override def onMessage(msg: Command): Behavior[Command] =
    batch -= 1
    if (batch <= 0 && !sendBatch()) {
      onDone ! Done
      Behaviors.stopped
    }
    this
}

case class EchoActorSupervisorAttrs(
    numMessagesPerActorPair: Int,
    numActors: Int,
    dispatcher: String,
    batchSize: Int
)
object EchoActorSupervisor {
  case class Completed(respondTo: Long)
  case class Start(respondTo: ActorRef[Completed])
  def apply(
      attrs: EchoActorSupervisorAttrs
  ): Behavior[Start] =
    Behaviors.setup(context => new EchoActorSupervisor(context, attrs))
}
class EchoActorSupervisor(
    context: ActorContext[EchoActorSupervisor.Start],
    attrs: EchoActorSupervisorAttrs
) extends AbstractBehavior[EchoActorSupervisor.Start](context) {
  import attrs.*
  import EchoActorSupervisor._
  override def onMessage(msg: Start) = {
    context.spawnAnonymous(
      EchoBenchSessionActor(
        EchoBenchSessionActorAttrs(
          msg.respondTo,
          numMessagesPerActorPair,
          numActors,
          dispatcher,
          batchSize
        )
      )
    )
    this
  }
}

case class EchoBenchSessionActorAttrs(
    respondTo: ActorRef[EchoActorSupervisor.Completed],
    numMessagesPerActorPair: Int,
    numActors: Int,
    dispatcher: String,
    batchSize: Int
)
object EchoBenchSessionActor {
  def apply(
      attrs: EchoBenchSessionActorAttrs
  ): Behavior[Done] =
    Behaviors.setup(context => new EchoBenchSessionActor(context, attrs))

  def printProgress(
      totalMessages: Long,
      numActors: Int,
      startNanoTime: Long
  ) = {
    val durationMicros = (System.nanoTime() - startNanoTime) / 1000
    println(
      f"  $totalMessages messages by $numActors actors took ${durationMicros / 1000} ms, " +
        f"${totalMessages.toDouble / durationMicros}%,.2f M msg/s"
    )
  }
}
class EchoBenchSessionActor(
    context: ActorContext[Done],
    attrs: EchoBenchSessionActorAttrs
) extends AbstractBehavior[Done](context) {
  import attrs.*
  import EchoBenchSessionActor._

  val engine = Engine
    .newBuilder()
    .option("engine.Mode", "throughput")
    .build()

  val numPairs = numActors / 2
  val props =
    Props.empty.withDispatcherFromConfig("akka.actor." + dispatcher)
  val pairs = (1 to numPairs).map { _ =>
    context.spawnAnonymous(
      EchoSenderActor(
        EchoSenderActorAttrs(
          context.self,
          numMessagesPerActorPair,
          batchSize,
          props,
          engine
        )
      ),
      props
    )
  }
  val startNanoTime = System.nanoTime()
  pairs.foreach(_ ! Message)
  var interactionsLeft = numPairs

  override def onMessage(msg: Done) = {
    interactionsLeft -= 1
    if (interactionsLeft == 0) {
      val totalNumMessages = numPairs * numMessagesPerActorPair
      printProgress(totalNumMessages, numActors, startNanoTime)
      respondTo ! EchoActorSupervisor.Completed(startNanoTime)
      Behaviors.stopped
    } else {
      this
    }
  }
  override def onSignal: PartialFunction[Signal, Behavior[Done]] = {
    case PostStop =>
      engine.close()
      this
  }
}
