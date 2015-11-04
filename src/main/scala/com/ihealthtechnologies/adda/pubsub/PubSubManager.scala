/**
 * Copyright (C) ${project.inceptionYear} Mycila (mathieu.carbou@gmail.com)
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

import akka.actor.{ ActorRef, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.OnNext

/**
 * An actor can either be a publisher or a subscriber, never both.
 *
 * Subscribers are the stream sinks, from which the items to publish arrive.
 * Publishers are the stream sources, which end up publishing the items.
 */
case class PubSubManager(
  nextUniqueActorId: Long = 0L,
  awaitingCompleted: List[ActorRef] = Nil,
  subscribers: Set[ActorRef] = Set.empty[ActorRef],
  publishers: Set[ActorRef] = Set.empty[ActorRef]) {

  def broadcastToSubscribers(toBroadcast: OnNext): Unit = {
    subscribers.foreach(_ ! toBroadcast)
  }

  def bulkBroadcastToSubscribers(bulk: Queue[_]): Unit = {
    subscribers.foreach(_ ! bulk)
  }

  def addSubscriber(subscriber: ActorRef): PubSubManager = {
    this.copy(
      nextUniqueActorId = nextUniqueActorId + 1,
      subscribers = subscribers + subscriber)
  }

  def addPublisher(publisher: ActorRef): PubSubManager = {
    this.copy(
      nextUniqueActorId = nextUniqueActorId + 1,
      publishers = publishers + publisher)
  }

  def removePublisher(publisher: ActorRef): PubSubManager = {
    this.copy(publishers = publishers - publisher)
  }

  def removeSubscriber(subscriber: ActorRef): PubSubManager = {
    this.copy(subscribers = subscribers - subscriber)
  }

  def awaitingCompleted(waiting: ActorRef): PubSubManager = {
    this.copy(awaitingCompleted = waiting :: awaitingCompleted)
  }

  def isCompleted: Boolean = subscribers.isEmpty && publishers.isEmpty

}
