package com.adda.pubsub

import akka.actor.{ActorRef, actorRef2Scala}

/**
 * An actor can either be a publisher or a subscriber, never both.
 */
class PubSubManager {

  private[this] var awaitingCompleted: List[ActorRef] = Nil
  private[this] val subscribers = new MemberManager[ActorRef]
  private[this] val publishers = new MemberManager[ActorRef]

  def addSubscriber(topic: String, subscriber: ActorRef) {
    subscribers.addMember(topic, subscriber)
  }

  def addPublisher(topic: String, publisher: ActorRef) {
    publishers.addMember(topic, publisher)
  }

  def subscribersForTopic(topic: String): Set[ActorRef] = {
    subscribers.membersForTopic(topic)
  }

  def publishersForTopic(topic: String): Set[ActorRef] = {
    publishers.membersForTopic(topic)
  }

  def topicForSubscriber(subscriber: ActorRef): String = {
    subscribers.topicForMember(subscriber)
  }

  /**
   * Has to be either a publisher or a subscriber.
   */
  def remove(actor: ActorRef) {
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

  def awaitingCompleted(waiting: ActorRef) {
    awaitingCompleted ::= waiting
    if (isCompleted) notifyCompleted()
  }

  private[this] def isSubscriber(member: ActorRef): Boolean = {
    subscribers.isMember(member)
  }

  private[this] def removeSubscriber(subscriber: ActorRef) {
    val topic = topicForSubscriber(subscriber)
    subscribers.removeMember(subscriber)
    val remainingSubscribers = subscribersForTopic(topic)
    if (remainingSubscribers.isEmpty) {
      publishersForTopic(topic).foreach(_ ! Complete)
    }
  }

  private[this] def removePublisher(publisher: ActorRef) {
    publishers.removeMember(publisher)
  }

  private[this] def isPublisher(member: ActorRef): Boolean = {
    publishers.isMember(member)
  }

  private[this] def isCompleted = !subscribers.hasMembers && !publishers.hasMembers

  private[this] def notifyCompleted() {
    awaitingCompleted.foreach(_ ! Completed)
    awaitingCompleted = Nil
  }

}
