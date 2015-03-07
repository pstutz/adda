package com.adda.pubsub

import scala.collection.mutable
import scala.language.postfixOps
import scala.reflect.ClassTag

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.actor.ActorSubscriberMessage.OnNext

final case object Complete

class AddaPublisher[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  val queue = mutable.Queue.empty[C]
  var completeReceived = false

  def receive = LoggingReceive {
    case OnNext(e) =>
      e match {
        case successfulMatch: C =>
          queue += successfulMatch
          publishNext()
      }
    case Request(cnt) =>
      publishNext()
    case Complete =>
      completeReceived = true
      publishNext()
    case Cancel =>
      context.stop(self)
  }

  def publishNext(): Unit = {
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
