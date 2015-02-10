package com.adda.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, Props, actorRef2Scala}
import akka.stream.actor.ActorPublisherMessage.{Cancel, Request}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{ActorPublisher, ActorSubscriber, WatermarkRequestStrategy}
import akka.util.Timeout

import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

case class AddaEntity[C: ClassTag](entity: C)

case class CreatePublisher[C: ClassTag]() {
  val props = Props(new SourceActor[C]())
  val name = ???
}

class BroadcastActor extends Actor with ActorLogging {
  implicit val timeout = Timeout(10 seconds)

  def receive = {
    case c@CreatePublisher() =>
      //TODO: Get a naming convention here
      val publisherActor = context.actorOf(c.props, "")
      sender ! publisherActor
    case a @ AddaEntity(e) => {
      try {
        Await.result(context.actorSelection(e.getClass.toString).resolveOne, timeout.duration)
      } catch {
        case ex: akka.actor.ActorNotFound => //context.actorOf(Props(new SourceActor[e.type]), e.getClass.toString)
        case _ =>
      }
      context.children.foreach(_ ! a)
    }
    case other =>
      log.error(s"[BroadcastActor] received unhandled message $other.")
  }

}

class SourceActor[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  val publishedClass: Class[C] = implicitly[ClassTag[C]].runtimeClass.asInstanceOf[Class[C]]

  val queue = mutable.Queue.empty[C]

  def receive = {
    case a @ AddaEntity(e) =>
      e match {
        case successfulMatch: C =>
          queue += successfulMatch
          publishNext()
        case noMatch => // Do nothing.
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
      val next = queue.dequeue
      onNext(next)
    }
  }

}

class SinkActor(val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {
  val requestStrategy = WatermarkRequestStrategy(50)

  def receive = {
    case OnNext(next: AnyRef) =>
      log.debug(s"[SinkActor] received new message $next.")
      broadcastActor ! AddaEntity(next)
    case OnError(err: Exception) =>
      context.stop(self)
    case OnComplete =>
      context.stop(self)
    case other =>
      log.error(s"[SinkActor] received unhandled message $other.")
  }
}
