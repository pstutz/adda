package com.adda.pubsub

import scala.collection.mutable
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag

import com.adda.interfaces.GraphSerializable
import com.adda.interfaces.TripleStore

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
import akka.util.Timeout

case class AddaEntity[C: ClassTag](entity: C)

case class CreatePublisher[C: ClassTag]() {
  val props = Props(new SourceActor[C]())
}

class BroadcastActor(private[this] val store: TripleStore) extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(10 seconds)

  def receive = {
    case c @ CreatePublisher() =>
      //TODO: Get a naming convention here
      val publisherActor = context.actorOf(c.props)
      sender ! publisherActor
    case a @ AddaEntity(e) =>
      e match {
        // If the entity is graph serializable, add it to the store.
        case g: GraphSerializable =>
          val triples = g.asGraph
          triples.foreach(store.addTriple(_))
      }
      context.children.foreach(_ ! a)
    case other =>
      log.error(s"[BroadcastActor] received unhandled message $other.")
  }

}

class SourceActor[C: ClassTag] extends ActorPublisher[C] with ActorLogging {

  private[this] val publishedClass: Class[C] = implicitly[ClassTag[C]].runtimeClass.asInstanceOf[Class[C]]

  private[this] val queue = mutable.Queue.empty[C]

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

class SinkActor(private[this] val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {

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
