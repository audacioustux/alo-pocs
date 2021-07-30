// import akka.actor.typed.ActorRef
// import akka.actor.typed.ActorSystem
// import akka.actor.typed.Behavior
// import akka.actor.typed.scaladsl.Behaviors
//
// object GreeterMain {
//   final case class Start(n: Int)
//
//   def greeter = Behaviors.receiveMessage { message =>
//     Behaviors.same
//   }
//
//   def apply(): Behavior[Start] =
//     Behaviors.receive { (ctx, msg) =>
//       Thread.sleep(5000)
//       var actorref: Option[_] = None
//       (1 to msg.n).map { n =>
//         val _actorref = ctx.spawn(greeter, n.toString())
//         if (.5 < Math.random() && actorref.isEmpty) {
//           actorref = Some(_actorref)
//         }
//       }
//       Behaviors.same
//     }
//   @main def hello: Unit = {
//     Thread.sleep(10000)
//     val greeterMain: ActorSystem[Start] =
//       ActorSystem(GreeterMain(), "AkkaQuickStart")
//     greeterMain ! GreeterMain.Start(1000000)
//
//   }
// }
// import akka.actor.typed.ActorSystem
// import akka.actor.typed.Behavior
// import akka.actor.typed.scaladsl.AbstractBehavior
// import akka.actor.typed.scaladsl.ActorContext
// import akka.actor.typed.scaladsl.Behaviors
// import akka.actor.typed.ActorRef
//
// object PrintMyActorRefActor {
//   def apply(): Behavior[String] =
//     Behaviors.setup(context => new PrintMyActorRefActor(context))
// }
//
// class PrintMyActorRefActor(context: ActorContext[String])
//     extends AbstractBehavior[String](context) {
//
//   override def onMessage(msg: String): Behavior[String] =
//     msg match {
//       case "printit" =>
//         this
//     }
// }
//
// object Main {
//   def apply(): Behavior[String] =
//     Behaviors.setup(context => new Main(context))
//
//   @main def init: Unit = {
//     Thread.sleep(5000)
//     val testSystem = ActorSystem(Main(), "testSystem")
//     testSystem ! "start"
//
//     Thread.sleep(1000)
//     System.gc()
//     System.gc()
//     System.gc()
//   }
// }
//
// class Main(context: ActorContext[String])
//     extends AbstractBehavior[String](context) {
//   override def onMessage(msg: String): Behavior[String] =
//     msg match {
//       case "start" =>
//         (1 to 1000000).map { n =>
//           context.spawn(PrintMyActorRefActor(), n.toString())
//         }
//
//         this
//     }
// }
import akka.persistence.typed.scaladsl.EventSourcedBehavior
import akka.persistence.typed.PersistenceId
import akka.actor.typed._
import akka.actor.typed.scaladsl._
import com.typesafe.config.ConfigFactory

object MyPersistentBehavior {
  sealed trait Command
  sealed trait Event
  final case class State()

  def apply(): Behavior[Command] =
    EventSourcedBehavior[Command, Event, State](
      persistenceId = PersistenceId.ofUniqueId("abc"),
      emptyState = State(),
      commandHandler = (state, cmd) =>
        throw new NotImplementedError(
          "TODO: process the command & return an Effect"
        ),
      eventHandler = (state, evt) =>
        throw new NotImplementedError(
          "TODO: process the event return the next state"
        )
    )
}
class Main(context: ActorContext[String])
    extends AbstractBehavior[String](context) {
  override def onMessage(msg: String): Behavior[String] =
    msg match {
      case "start" =>
        (1 to 1000000).map { n =>
          context.spawn(MyPersistentBehavior(), n.toString())
        }
        this
    }
}
object Main {
  def apply(): Behavior[String] =
    Behaviors.setup(context => new Main(context))

  @main def init: Unit = {
    System.out.println(ProcessHandle.current().pid())
    Thread.sleep(5000)
    val testSystem = ActorSystem(
      Main(),
      "testSystem",
      ConfigFactory.load(
        ConfigFactory.parseString(
          """
          akka {
            extensions = [akka.persistence.Persistence]
            persistence {
              journal {
                plugin = "akka.persistence.journal.leveldb"
              }

              snapshot-store {
                plugin = "akka.persistence.snapshot-store.local"
              }

            }

          }
          """
        )
      )
    )
    testSystem ! "start"
  }
}

// import org.graalvm.polyglot.{Engine, Context}
//
// @main def main: Unit = {
//   Thread.sleep(5000)
//   val polyglotEngine = Engine.newBuilder().build();
//   (1 to 1000000).map { n =>
//     val ctx = Context.newBuilder().engine(polyglotEngine).build();
//     ctx.eval("js", "1");
//     ctx.close();
//   }
//   polyglotEngine.close();
//   System.out.println("done")
//   System.gc()
//   Thread.sleep(5000)
// }
