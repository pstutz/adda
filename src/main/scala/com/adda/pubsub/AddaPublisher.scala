package com.adda.pubsub

import scala.collection.mutable
import scala.language.postfixOps
import scala.reflect.ClassTag
import akka.actor.{ Actor, ActorLogging }
import akka.event.LoggingReceive
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{ Cancel, Request }
import akka.stream.actor.ActorSubscriberMessage.OnNext
import scala.collection.immutable.Queue
import java.util.ArrayDeque

final case object Complete

class AddaPublisher[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  val queue = new ArrayDeque[C]()
  var completeReceived = false

  def receive: Actor.Receive = LoggingReceive {
    case OnNext(e: C) =>
      queue.addLast(e)
      if (isActive) publishNext()
    case bulk: Queue[C] =>
      for (e <- bulk) queue.addLast(e)
      if (isActive) publishNext()
    case Request(cnt) =>
      publishNext()
    case Complete =>
      completeReceived = true
      if (isActive) publishNext()
    case Cancel =>
      context.stop(self)
  }

  def publishNext(): Unit = {
    while (totalDemand > 0 && !queue.isEmpty) {
      val next = queue.removeFirst
      onNext(next)
    }
    if (completeReceived && queue.isEmpty) {
      onComplete()
      context.stop(self)
    }
  }

}
