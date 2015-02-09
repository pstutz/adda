package com.adda.pubsub

import java.util.concurrent.LinkedBlockingQueue

import scala.reflect.ClassTag

import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.actor.ActorSubscriberMessage.OnError
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.WatermarkRequestStrategy

case class AddaEntity[C: ClassTag](entity: C)

case class CreatePublisher[C: ClassTag]() {
  val props = Props(new SourceActor[C]())
}

class BroadcastActor extends Actor with ActorLogging {

  def receive = {
    case c @ CreatePublisher() =>
      val publisherActor = context.actorOf(c.props)
      sender ! publisherActor
    case a @ AddaEntity(e) =>
      context.children.foreach(_ ! a)
    case other =>
      log.error(s"[BroadcastActor] received unhandled message $other.")
  }

}

class SourceActor[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  val publishedClass: Class[C] = implicitly[ClassTag[C]].runtimeClass.asInstanceOf[Class[C]]

  val queue = new LinkedBlockingQueue[C]

  def receive = {
    case a @ AddaEntity(e) =>
      if (publishedClass.isAssignableFrom(e.getClass)) {
        queue.put(e.asInstanceOf[C])
        publishNext()
      }
    case Request(cnt) =>
      publishNext()
    case Cancel =>
      context.stop(self)
    case other =>
      log.error(s"[SourceActor] received unhandled message $other.")
  }

  def publishNext() {
    while (!queue.isEmpty && isActive && totalDemand > 0) {
      val next = queue.take
      onNext(next)
    }
  }

}

class SinkActor(val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {
  val requestStrategy = WatermarkRequestStrategy(50)

  def receive = {
    case OnNext(next: AnyRef) =>
      broadcastActor ! AddaEntity(next)
    case OnError(err: Exception) =>
      context.stop(self)
    case OnComplete =>
      context.stop(self)
    case other =>
      log.error(s"[SinkActor] received unhandled message $other.")
  }
}
