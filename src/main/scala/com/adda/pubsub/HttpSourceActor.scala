package com.adda.pubsub

import akka.actor.ActorLogging
import akka.event.LoggingReceive
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}

import scala.collection.mutable
import scala.reflect.ClassTag

class HttpSourceActor[C: ClassTag] extends ActorPublisher[C] with ActorLogging {
  
  private[this] val queue = mutable.Queue.empty[C]
  
  def receive = LoggingReceive {
    case e: C =>
      queue += e
      publishNext()
    case Request(cnt) =>
      publishNext()
    case Cancel => 
      context.stop(self)
  }

  def publishNext() {
    while (!queue.isEmpty && isActive && totalDemand > 0) {
      val next = queue.dequeue
      onNext(next)
    }
  }
}
