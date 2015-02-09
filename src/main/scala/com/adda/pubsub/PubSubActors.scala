package com.adda.pubsub

import akka.stream.actor.WatermarkRequestStrategy
import akka.actor.{ Actor, ActorLogging, ActorRef }
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import java.util.concurrent.LinkedBlockingQueue
import scala.reflect.ClassTag
import akka.actor.Props

case class AddaEntity[C: ClassTag](entity: C)

case class CreatePublisher[C: ClassTag]() {
  val props = Props(new SourceActor[C]())
}

class BroadcastActor extends Actor with ActorLogging {

  def receive = {
    case c @ CreatePublisher() =>
      log.debug(s"[BroadcastActor] Received ${c.props}.")
      val publisherActor = context.actorOf(c.props)
      sender ! publisherActor
    case a @ AddaEntity(e) =>
      log.debug(s"[BroadcastActor] Received $e, children = ${context.children.mkString(",")}.")
      context.children.foreach(_ ! a)
    case other => throw new Exception(s"[BroadcastActor] Received unhandled $other.")
  }

}

class SourceActor[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  val publishedClass: Class[C] = implicitly[ClassTag[C]].runtimeClass.asInstanceOf[Class[C]]

  val queue = new LinkedBlockingQueue[C]

  def receive = {
    case a @ AddaEntity(e) =>
      log.debug(s"[SourceActor] Received AddaEntity($e).")
      if (publishedClass.isAssignableFrom(e.getClass)) {
        queue.put(e.asInstanceOf[C])
        log.info(s"[SourceActor] Put $e into the queue!!!")
        publishNext()
      } else {
        log.info(s"[SourceActor] $e has class ${e.getClass.getName}, which did not match ${publishedClass.getName}.")
      }

    case Request(cnt) =>
      log.debug(s"[SourceActor] Received Request($cnt).")
      publishNext()
    case Cancel =>
      log.info("[SourceActor] Cancel Message Received -- Stopping.")
      context.stop(self)
    case other => throw new Exception(s"[SourceActor] Received unhandled $other.")
  }

  def publishNext() {
    while (!queue.isEmpty && isActive && totalDemand > 0) {
      val next = queue.take
      log.info(s"[SourceActor] I'm publishing $next!!!")
      onNext(next)
    }
  }

}

class SinkActor(val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {
  val requestStrategy = WatermarkRequestStrategy(50)

  def receive = {
    case OnNext(next: AnyRef) =>
      log.debug(s"[SinkActor] Received OnNext($next).")
      broadcastActor ! AddaEntity(next)
    case OnError(err: Exception) =>
      log.error(err, s"[SinkActor] Received Exception $err.")
      context.stop(self)
    case OnComplete =>
      log.info(s"[SinkActor] Stream Completed!")
      context.stop(self)
    case other => throw new Exception(s"[SinkActor] Received unhandled $other.")
  }
}
