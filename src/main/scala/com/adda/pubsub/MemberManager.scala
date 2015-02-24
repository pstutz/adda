package com.adda.pubsub

class MemberManager[M] {

  private[this] var activeMembers = Map.empty[String, Set[M]].withDefaultValue(Set.empty)
  private[this] var topics = Map.empty[M, String]

  def hasMembers: Boolean = activeMembers.nonEmpty

  def isMember(member: M): Boolean = {
    topics.contains(member)
  }

  def topicForMember(member: M): String = {
    topics(member)
  }

  def membersForTopic(topic: String): Set[M] = {
    activeMembers(topic)
  }

  def addMember(topic: String, member: M): Unit = {
    val updatedMembersForTopic = activeMembers(topic) + member
    activeMembers += (topic -> updatedMembersForTopic)
    topics += (member -> topic)
  }

  def removeMember(member: M): Unit = {
    val topic = topics(member)
    val updatedMembersForTopic = activeMembers(topic) - member
    if (updatedMembersForTopic.isEmpty) {
      activeMembers -= topic
    } else {
      activeMembers += (topic -> updatedMembersForTopic)
    }
    topics -= member
  }

}
