package com.ihtech.adda.pubsub

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
  nextUniqueActorId: Long = 0l,
  awaitingCompleted: List[ActorRef] = Nil,
  subscribers: Set[ActorRef] = Set.empty[ActorRef],
  publishers: Set[ActorRef] = Set.empty[ActorRef]) {

  def broadcastToPublishers(toBroadcast: OnNext): Unit = {
    publishers.foreach(_ ! toBroadcast)
  }

  def bulkBroadcastToPublishers(bulk: Queue[_]): Unit = {
    publishers.foreach(_ ! bulk)
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
