/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package com.ihealthtechnologies.adda.pubsub

import scala.collection.immutable.Queue

import akka.actor.{ Actor, ActorLogging, ActorRef, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.MaxInFlightRequestStrategy

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
    val broadcaster: ActorRef) extends ActorSubscriber with ActorLogging {

  val emptyQueue = Queue.empty[Any]
  val maxQueueSize = 100

  /**
   * The purpose of this strategy is to prevent the internal queue size of the
   * publisher from growing larger than `maxQueueSize`.
   */
  val requestStrategy = new MaxInFlightRequestStrategy(maxQueueSize) {
    override def inFlightInternally = queueSize
  }
  // Always updated with the current queue size.
  var queueSize = 0

  /**
   * Receive function that queues received elements whilst waiting for `CanPublishNext'.
   */
  def queuing(queued: Queue[Any], completed: Boolean, canPublishNext: Boolean): Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      if (canPublishNext) {
        broadcaster ! n
        become()
      } else {
        become(queued.enqueue(e))
      }
    case CanPublishNext =>
      publishNext(queued, completed = completed)
    case OnComplete =>
      if (canPublishNext) {
        handleCompletion
      } else {
        become(queued, completed = true)
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
          become(canPublishNext = true)
        }
      case Queue(singleElement) =>
        // OnNext is a light-weight wrapper compared to Queue, which internally maintains two lists.
        broadcaster ! OnNext(singleElement)
        become(completed = completed)
      case longerQueue: Any =>
        // TODO:  Once we distribute the design, ensure Kryo serializes queues efficiently.
        broadcaster ! longerQueue
        become(completed = completed)
    }
  }

  def become(queued: Queue[Any] = emptyQueue, completed: Boolean = false, canPublishNext: Boolean = false): Unit = {
    queueSize = queued.size
    context.become(queuing(queued, completed, canPublishNext))
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

