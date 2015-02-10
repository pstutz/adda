package com.adda.pubsub

import scala.collection.mutable
import scala.language.postfixOps
import scala.reflect.ClassTag

import akka.actor.ActorLogging
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.actor.ActorPublisherMessage.Request

final case object Complete

class SourceActor[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  private[this] val className = implicitly[ClassTag[C]].runtimeClass.getName

  private[this] val queue = mutable.Queue.empty[C]

  def receive = {
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
      onComplete()
      context.stop(self)
    case Cancel =>
      context.stop(self)
    case other =>
      log.error(s"[SourceActor] received unhandled message $other.")
  }

  def publishNext() {
    while (!queue.isEmpty && isActive && totalDemand > 0) {
      val next = queue.dequeue
      onNext(next)
    }
  }

}
