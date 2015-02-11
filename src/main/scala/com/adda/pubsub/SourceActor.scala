package com.adda.pubsub

import scala.collection.mutable
import scala.language.postfixOps
import scala.reflect.ClassTag
import akka.actor.ActorLogging
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.actor.ActorPublisherMessage.Request
import akka.actor.UnhandledMessage
import akka.event.LoggingReceive

final case object Complete

class SourceActor[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  private[this] val queue = mutable.Queue.empty[C]
  private[this] var completeReceived = false

  def receive = LoggingReceive {
    case a @ AddaEntity(e) =>
      e match {
        case successfulMatch: C =>
          queue += successfulMatch
          publishNext()
        case other => // Do nothing.
      }
    case Request(cnt) =>
      publishNext()
    case Complete =>
      completeReceived = true
      publishNext()
    case Cancel =>
      context.stop(self)
  }

  def publishNext() {
    while (!queue.isEmpty && isActive && totalDemand > 0) {
      val next = queue.dequeue
      onNext(next)
    }
    if (queue.isEmpty && completeReceived) {
      onComplete()
      context.stop(self)
    }
  }

}
