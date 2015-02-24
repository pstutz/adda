package com.adda.pubsub

import akka.actor.{ ActorRef, actorRef2Scala }

/**
 * An actor can either be a publisher or a subscriber, never both.
 *
 * Subscribers are the stream sinks, from which the items to publish arrive.
 * Publishers are the stream sources, which end up publishing the items.
 */
class PubSubManager {

  private[this] var awaitingCompleted: List[ActorRef] = Nil
  private[this] val subscribers = new MemberManager[ActorRef]
  private[this] val publishers = new MemberManager[ActorRef]

  def broadcastToPublishers[C <: AnyRef](fromSubscriber: ActorRef, itemToBroadcast: ToBroadcast[C]): Unit = {
    val topic = subscribers.topicForMember(fromSubscriber)
    publishersForTopic(topic).foreach(_ ! itemToBroadcast)
  }

  def addSubscriber(topic: String, subscriber: ActorRef): Unit = {
    subscribers.addMember(topic, subscriber)
  }

  def addPublisher(topic: String, publisher: ActorRef): Unit = {
    publishers.addMember(topic, publisher)
  }

  /**
   * Has to be either a publisher or a subscriber.
   */
  def remove(actor: ActorRef): Unit = {
    if (isSubscriber(actor)) {
      removeSubscriber(actor)
    } else if (isPublisher(actor)) {
      removePublisher(actor)
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

  private[this] def subscribersForTopic(topic: String): Set[ActorRef] = {
    subscribers.membersForTopic(topic)
  }

  private[this] def publishersForTopic(topic: String): Set[ActorRef] = {
    publishers.membersForTopic(topic)
  }

  private[this] def isSubscriber(member: ActorRef): Boolean = {
    subscribers.isMember(member)
  }

  private[this] def removeSubscriber(subscriber: ActorRef): Unit = {
    val topic = subscribers.topicForMember(subscriber)
    subscribers.removeMember(subscriber)
    val remainingSubscribers = subscribersForTopic(topic)
    if (remainingSubscribers.isEmpty) {
      publishersForTopic(topic).foreach(_ ! Complete)
    }
  }

  private[this] def removePublisher(publisher: ActorRef): Unit = {
    publishers.removeMember(publisher)
  }

  private[this] def isPublisher(member: ActorRef): Boolean = {
    publishers.isMember(member)
  }

  private[this] def isCompleted: Boolean = !subscribers.hasMembers && !publishers.hasMembers

  private[this] def notifyCompleted(): Unit = {
    awaitingCompleted.foreach(_ ! Completed)
    awaitingCompleted = Nil
  }

}
