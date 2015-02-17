package com.adda.pubsub

import akka.actor.ActorRef

class PubSubManager[M] {

  private[this] val subscribers = new MemberManager[M]
  private[this] val publishers = new MemberManager[M]

  def isCompleted = !subscribers.hasMembers && !publishers.hasMembers

  def addSubscriber(topic: String, subscriber: M) {
    subscribers.addMember(topic, subscriber)
  }

  def addPublisher(topic: String, publisher: M) {
    publishers.addMember(topic, publisher)
  }

  def removeSubscriber(subscriber: M) {
    subscribers.removeMember(subscriber)
  }

  def removePublisher(publisher: M) {
    publishers.removeMember(publisher)
  }

  def subscribersForTopic(topic: String): Set[M] = {
    subscribers.membersForTopic(topic)
  }

  def publishersForTopic(topic: String): Set[M] = {
    publishers.membersForTopic(topic)
  }

  def topicForPublisher(publisher: M): String = {
    publishers.topicForMember(publisher)
  }

  def isSubscriber(member: M): Boolean = {
    subscribers.isMember(member)
  }

  def isPublisher(member: M): Boolean = {
    publishers.isMember(member)
  }

}
