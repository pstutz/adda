package com.ihealthtechnologies.adda.pubsub

import akka.util.Timeout
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

import scala.collection.immutable.Queue

import akka.actor.{ Actor, ActorLogging, ActorRef, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.{ RequestStrategy, WatermarkRequestStrategy }
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }

object FlowControl {
  private[this] val highWatermark = 50
  val requestStrategy: RequestStrategy = WatermarkRequestStrategy(highWatermark)
}

/**
 * Publishes stream elements to the broadcaster actor for its type.
 */
class Publisher(
  val trackCompletion: Boolean,
  val broadcaster: ActorRef) extends ActorSubscriber with ActorLogging {

  val requestStrategy = FlowControl.requestStrategy

  def receive: Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
//        implicit val timeout = Timeout(5 seconds)
//        val future = broadcaster ? n
//        Await.result(future, timeout.duration)
          broadcaster ! n
    case OnComplete =>
        handleCompletion
    case OnError(e) =>
      reportError(e)
  }

  def handleCompletion(): Unit = {
    if (trackCompletion) broadcaster ! Completed
    context.stop(self)
  }

  def reportError(e: Throwable): Unit = {
    log.error(e, s"Adda sink received error ${e.getMessage} from $sender.")
  }

}
