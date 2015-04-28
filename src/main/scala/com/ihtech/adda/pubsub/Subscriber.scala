package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue
import scala.language.postfixOps
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorLogging, Stash }
import akka.event.LoggingReceive
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.actor.ActorSubscriberMessage.OnNext

final case object Complete

class Subscriber[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  private[this] val emptyQueue = Queue.empty[C]

  /**
   * Queuing mode that stores both the queued elements and if the stream has been completed.
   */
  def queuing(queued: Queue[C], completed: Boolean): Actor.Receive = LoggingReceive {
    case OnNext(e: C) =>
      val updatedQueue = queued.enqueue(e)
      val remainingQueue = deliverFromQueue(updatedQueue, completed)
      context.become(queuing(remainingQueue, completed))
    case q: Queue[C] =>
      val updatedQueue = queued.enqueue(q)
      val remainingQueue = deliverFromQueue(updatedQueue, completed)
      context.become(queuing(remainingQueue, completed))
    case Request(cnt) =>
      val remaining = deliverFromQueue(queued, completed)
      context.become(queuing(remaining, completed))
    case Complete =>
      val remaining = deliverFromQueue(queued, true)
      context.become(queuing(remaining, true))
    case Cancel =>
      context.stop(self)
  }

  def receive: Actor.Receive = queuing(emptyQueue, false)

  /**
   * Delivers from `q' whatever it can, then returns a queue with the remaining items.
   * Completes if all items were delivered and the stream has completed.
   */
  def deliverFromQueue(q: Queue[C], completed: Boolean): Queue[C] = {
    if (totalDemand >= q.size) {
      q.foreach(onNext(_))
      if (completed) {
        onComplete()
        context.stop(self)
      }
      emptyQueue
    } else if (totalDemand == 0) {
      q
    } else {
      val (toDeliver, remaining) = q.splitAt(totalDemand.toInt)
      toDeliver.foreach(onNext(_))
      remaining
    }
  }

}
