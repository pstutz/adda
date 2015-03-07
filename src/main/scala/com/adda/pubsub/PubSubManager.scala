package com.adda.pubsub

import scala.reflect.ClassTag
import akka.actor.{ ActorRef, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.OnNext

/**
 * An actor can either be a publisher or a subscriber, never both.
 *
 * Subscribers are the stream sinks, from which the items to publish arrive.
 * Publishers are the stream sources, which end up publishing the items.
 */
class PubSubManager {

  private[this] var awaitingCompleted: List[ActorRef] = Nil
  private[this] var subscribers = Set.empty[ActorRef]
  private[this] var publishers = Set.empty[ActorRef]

  def broadcastToPublishers(toBroadcast: OnNext): Unit = {
    publishers.foreach(_ ! toBroadcast)
  }

  def addSubscriber(subscriber: ActorRef): Unit = {
    subscribers += subscriber
  }

  def addPublisher(publisher: ActorRef): Unit = {
    publishers += publisher
  }

  /**
   * Has to be either a publisher or a subscriber.
   */
  def remove(actor: ActorRef): Unit = {
    if (subscribers.contains(actor)) {
      subscribers -= actor
      if (subscribers.isEmpty) {
        publishers.foreach(_ ! Complete)
      }
    } else if (publishers.contains(actor)) {
      publishers -= actor
    } else {
      throw new Exception(
        s"Actor needs to be either a publisher or a subscriber. $actor is neither.")
    }
    if (isCompleted) notifyCompleted()
  }

  def awaitingCompleted(waiting: ActorRef): Unit = {
    awaitingCompleted ::= waiting
    if (isCompleted) notifyCompleted()
  }

  private[this] def isCompleted: Boolean = subscribers.isEmpty && publishers.isEmpty

  private[this] def notifyCompleted(): Unit = {
    awaitingCompleted.foreach(_ ! Completed)
    awaitingCompleted = Nil
  }

}
