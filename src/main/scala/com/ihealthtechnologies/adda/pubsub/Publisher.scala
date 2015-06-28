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
 * Queues elements until the last element or batch of elements were delivered in
 * order to guarantee that elements are delivered to the subscribers in the same order
 * as they were received at this publisher. This is necessary, because the broadcaster
 * uses futures and given their asynchronous execution the ordered delivery would
 * otherwise not be guaranteed.
 */
class Publisher(
  val trackCompletion: Boolean,
  val broadcaster: ActorRef,
  val MaxQueueSize: Int = 100000) extends ActorSubscriber with ActorLogging {

  private[this] val emptyQueue = Queue.empty[Any]

  val requestStrategy = FlowControl.requestStrategy

  /**
   * Receive function that queues received elements whilst waiting for `CanPublishNext'.
   */
  def queuing(queued: Queue[Any], completed: Boolean, canPublishNext: Boolean): Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      if (canPublishNext) {
        implicit val timeout = Timeout(5 seconds)
        val future = broadcaster ? n
        val result = Await.result(future, timeout.duration)
        self ! result
        context.become(queuing(emptyQueue, false, false))
      } else {
        context.become(queuing(queued.enqueue(e), false, false))
      }
    case CanPublishNext =>
      publishNext(queued, completed)
    case OnComplete =>
      if (canPublishNext) {
        handleCompletion
      } else {
        context.become(queuing(queued, true, false))
      }
    case OnError(e) =>
      reportError(e)
  }

  def publishNext(queued: Queue[Any], completed: Boolean): Unit = {
    queued match {
      case `emptyQueue` =>
        if (completed) {
          handleCompletion
        } else {
          context.become(queuing(emptyQueue, false, true))
        }
      case Queue(singleElement) =>
        // OnNext is a light-weight wrapper compared to Queue, which internally maintains two lists.
        broadcaster ! OnNext(singleElement)
        context.become(queuing(emptyQueue, completed, false))
      case longerQueue: Any =>
        // TODO:  Once we distribute the design, ensure Kryo serializes queues efficiently.
        broadcaster ! longerQueue
        context.become(queuing(emptyQueue, completed, false))
    }
  }

  def handleCompletion(): Unit = {
    if (trackCompletion) broadcaster ! Completed
    context.stop(self)
  }

  def receive: Actor.Receive = queuing(emptyQueue, false, true)

  def reportError(e: Throwable): Unit = {
    log.error(e, s"Adda sink received error ${e.getMessage} from $sender.")
  }

}
