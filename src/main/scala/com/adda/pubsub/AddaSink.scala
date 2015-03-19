package com.adda.pubsub

import scala.collection.immutable.Queue

import akka.actor.{ Actor, ActorLogging, ActorRef, Stash, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.WatermarkRequestStrategy

class AddaSink(
  val isTemporary: Boolean,
  val broadcaster: ActorRef) extends ActorSubscriber with ActorLogging with Stash {

  import context.become
  val emptyQueue = Queue.empty[Any]

  val requestStrategy = WatermarkRequestStrategy(50)

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
        become(queuing(emptyQueue))
      }
    case OnComplete =>
      stash()
    case OnError(e) =>
      handleError(e)
  }

  def receive: Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      broadcaster ! n
      become(queuing(emptyQueue))
    case CanSendNext =>
      throw new Exception("AddaSink received CanSendNext, but was in queuing mode.")
    case OnComplete =>
      if (!isTemporary) broadcaster ! Completed
      context.stop(self)
    case OnError(e) =>
      handleError(e)
  }

  def handleError(e: Throwable): Unit = {
    log.error(e, s"Adda sink received error ${e.getMessage} from $sender")
    e.printStackTrace
  }

}
