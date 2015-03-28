package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue

import akka.actor.{ Actor, ActorLogging, ActorRef, Stash, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.{ RequestStrategy, WatermarkRequestStrategy }
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }

case class IllegalActorState(msg: String) extends Exception(msg)

object FlowControl {
  private[this] val highWatermark = 50
  val requestStrategy: RequestStrategy = WatermarkRequestStrategy(highWatermark)
}

/**
 * Publishes stream elements to the broadcaster actor for its type.
 * Queues elements until the last element or batch of elements were delivered in
 * order to guarantee that elements are delivered to the subscribers in the same order
 * as they were received at this publisher. This is necessary, because the broadcaster
 * uses futures and given their asynchronous execution the ordered delivery would
 * otherwise not be guaranteed.
 */
class Publisher(
  val trackCompletion: Boolean,
  val broadcaster: ActorRef) extends ActorSubscriber with ActorLogging with Stash {

  private[this] val emptyQueue = Queue.empty[Any]

  val requestStrategy = FlowControl.requestStrategy

  /**
   * Receive function that queues received elements whilst waiting for `CanPublishNext'.
   */
  def queuing(queued: Queue[Any]): Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      context.become(queuing(queued.enqueue(e)))
    case CanPublishNext =>
      queued match {
        case `emptyQueue` =>
          unstashAll()
          context.become(receive)
        case Queue(singleElement) =>
          // OnNext is a light-weight wrapper compared to Queue, which internally maintains two lists.
          broadcaster ! OnNext(singleElement)
          context.become(queuing(emptyQueue))
        case longerQueue =>
          // TODO:  Once we distribute the design, ensure Kryo serializes queues efficiently.
          broadcaster ! longerQueue
          context.become(queuing(emptyQueue))
      }
    case OnComplete =>
      stash()
    case OnError(e) =>
      reportError(e)
  }

  def receive: Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      broadcaster ! n
      context.become(queuing(emptyQueue))
    case CanPublishNext =>
      val msg = "Publisher received CanPublishNext, but was not in queueing mode."
      log.error(new IllegalActorState(msg), msg)
    case OnComplete =>
      if (trackCompletion) broadcaster ! Completed
      context.stop(self)
    case OnError(e) =>
      reportError(e)
  }

  def reportError(e: Throwable): Unit = {
    log.error(e, s"Adda sink received error ${e.getMessage} from $sender.")
  }

}
