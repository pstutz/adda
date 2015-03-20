package com.adda.pubsub

import scala.collection.immutable.Queue

import akka.actor.{ Actor, ActorLogging, ActorRef, Stash, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.{ RequestStrategy, WatermarkRequestStrategy }
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }

object FlowControl {
  private[this] val highWatermark = 50
  val requestStrategy: RequestStrategy = WatermarkRequestStrategy(highWatermark)
}

class AddaSink(
  val trackCompletion: Boolean,
  val broadcaster: ActorRef) extends ActorSubscriber with ActorLogging with Stash {

  private[this] val emptyQueue = Queue.empty[Any]

  val requestStrategy = FlowControl.requestStrategy

  /**
   * Receive function that queues received elements whilst waiting for `CanSendNext'.
   */
  def queuing(queued: Queue[Any]): Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      context.become(queuing(queued.enqueue(e)))
    case CanSendNext =>
      if (queued.isEmpty) {
        unstashAll()
        context.become(receive)
      } else {
        broadcaster ! queued
        context.become(queuing(emptyQueue))
      }
    case OnComplete =>
      stash()
    case OnError(e) =>
      handleError(e)
  }

  def receive: Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      broadcaster ! n
      context.become(queuing(emptyQueue))
    case CanSendNext =>
      throw new Exception("AddaSink received CanSendNext, but was in queuing mode.")
    case OnComplete =>
      if (trackCompletion) broadcaster ! Completed
      context.stop(self)
    case OnError(e) =>
      handleError(e)
  }

  def handleError(e: Throwable): Unit = {
    log.error(e, s"Adda sink received error ${e.getMessage} from $sender")
    e.printStackTrace
  }

}
