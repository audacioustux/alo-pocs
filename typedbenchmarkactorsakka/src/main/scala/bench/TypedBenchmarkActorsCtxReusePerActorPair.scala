/*
 * Copyright (C) 2009-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package bench

import java.util.concurrent.CountDownLatch

import scala.concurrent.duration._

import akka.Done
import akka.actor.typed.scaladsl.ActorContext
import akka.actor.typed.scaladsl.Behaviors

import akka.actor.typed.ActorRef
import akka.actor.typed.Behavior
import akka.actor.typed.Props
import akka.actor.typed.PostStop

import org.graalvm.polyglot._

object TypedBenchmarkActorsCtxReusePerActorPair {

  // to avoid benchmark to be dominated by allocations of message
  // we pass the respondTo actor ref into the behavior
  case object Message

  def echoBehavior(
      respondTo: ActorRef[Message.type],
      polyCtx: Context,
      jsSource: Source
  ): Behavior[Message.type] = Behaviors.receive { (_, _) =>
    polyCtx.eval(jsSource)

    respondTo ! Message
    echoBehavior(respondTo, polyCtx, jsSource)
  }
  private def echoSender(
      messagesPerPair: Int,
      onDone: ActorRef[Done],
      batchSize: Int,
      childProps: Props,
      engine: Engine
  ): Behavior[Message.type] =
    Behaviors.setup { ctx =>
      val polyCtx = Context
        .newBuilder()
        .allowAllAccess(true)
        .engine(engine)
        .build()
      val jsSource =
        Source
          .newBuilder(
            "js",
            "'.'",
            "dummymodule"
          )
          .build()
      val echo =
        ctx.spawn(
          echoBehavior(
            ctx.self,
            polyCtx,
            jsSource
          ),
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
        } else
          false
      }

      Behaviors.receiveMessage { _ =>
        batch -= 1
        if (batch <= 0 && !sendBatch()) {
          onDone ! Done
          polyCtx.close()
          // Behaviors.same
          Behaviors.stopped
        } else {
          Behaviors.same
        }
      }
    }

  case class Start(respondTo: ActorRef[Completed])
  case class Completed(startNanoTime: Long)

  def echoActorsSupervisor(
      numMessagesPerActorPair: Int,
      numActors: Int,
      dispatcher: String,
      batchSize: Int
  ): Behavior[Start] =
    Behaviors.receive { (ctx, msg) =>
      msg match {
        case Start(respondTo) =>
          // note: no protection against accidentally running bench sessions in parallel
          val sessionBehavior =
            startEchoBenchSession(
              numMessagesPerActorPair,
              numActors,
              dispatcher,
              batchSize,
              respondTo
            )
          ctx.spawnAnonymous(sessionBehavior)
          Behaviors.same
      }
    }

  private def startEchoBenchSession(
      messagesPerPair: Int,
      numActors: Int,
      dispatcher: String,
      batchSize: Int,
      respondTo: ActorRef[Completed]
  ): Behavior[Unit] = {

    val numPairs = numActors / 2

    Behaviors
      .setup[Any] { ctx =>
        val engine = Engine
          .newBuilder()
          .option("engine.Mode", "throughput")
          .build()
        val props =
          Props.empty.withDispatcherFromConfig("akka.actor." + dispatcher)
        val pairs = (1 to numPairs).map { _ =>
          ctx.spawnAnonymous(
            echoSender(
              messagesPerPair,
              ctx.self.narrow[Done],
              batchSize,
              props,
              engine
            ),
            props
          )
        }
        val startNanoTime = System.nanoTime()
        pairs.foreach(_ ! Message)
        var interactionsLeft = numPairs
        Behaviors
          .receiveMessagePartial { case Done =>
            interactionsLeft -= 1
            if (interactionsLeft == 0) {
              val totalNumMessages = numPairs * messagesPerPair
              printProgress(totalNumMessages, numActors, startNanoTime)
              respondTo ! Completed(startNanoTime)
              Behaviors.stopped
              // Behaviors.same
            } else {
              Behaviors.same
            }
          }
          .receiveSignal { case (context, PostStop) =>
            engine.close()
            Behaviors.same
          }
      }
      .narrow[Unit]
  }

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
