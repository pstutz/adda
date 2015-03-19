package com.adda.pubsub

import scala.collection.mutable
import scala.language.postfixOps
import scala.reflect.ClassTag
import akka.actor.{ Actor, ActorLogging }
import akka.event.LoggingReceive
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.actor.ActorSubscriberMessage.OnNext
import scala.collection.immutable.Queue
import java.util.ArrayDeque
import akka.actor.Stash

final case object Complete

class AddaSource[C: ClassTag] extends ActorPublisher[C] with ActorLogging with Stash {

  import context._

  val emptyQueue = Queue.empty[C]

  /**
   * Queuing mode that is enabled when the total demand is 0.
   */
  def queuing(queued: Queue[C]): Actor.Receive = LoggingReceive {
    case OnNext(e: C) =>
      become(queuing(queued.enqueue(e)))
    case q: Queue[C] =>
      become(queuing(queued.enqueue(q)))
    case Request(cnt) =>
      val remaining = deliverFromQueue(queued)
      if (remaining == emptyQueue) {
        unstashAll()
        become(receive)
      } else {
        become(queuing(remaining))
      }
    case Complete =>
      stash()
    case Cancel =>
      stop(self)
  }

  def receive: Actor.Receive = LoggingReceive {
    case OnNext(e: C) =>
      if (totalDemand > 0) {
        onNext(e)
      } else {
        become(queuing(Queue(e)))
      }
    case q: Queue[C] =>
      val remaining = deliverFromQueue(q)
      if (remaining != emptyQueue) {
        become(queuing(remaining))
      }
    case Request(cnt) =>
    case Complete =>
      onComplete()
      stop(self)
    case Cancel =>
      stop(self)
  }

  /**
   * Delivers from `q' whatever it can, then returns a queue with the remaining items.
   */
  def deliverFromQueue(q: Queue[C]): Queue[C] = {
    if (totalDemand >= q.size) {
      q.foreach(onNext(_))
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
