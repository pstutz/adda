/**
 * Copyright (C) 2015 Cotiviti Labs (nexgen.admin@cotiviti.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ihealthtechnologies.adda.pubsub

import scala.collection.immutable.Queue
import scala.language.postfixOps
import scala.reflect.ClassTag

import akka.actor.{ Actor, ActorLogging }
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
