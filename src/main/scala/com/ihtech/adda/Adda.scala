package com.ihtech.adda

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

import org.reactivestreams.{ Publisher, Subscriber }

import com.ihtech.adda.interfaces.PubSub
import com.ihtech.adda.pubsub.{ AwaitCompleted, Broadcaster, CreatePublisher, CreateSubscriber }

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.event.Logging
import akka.pattern.ask
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.{ ActorPublisher, ActorSubscriber }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout

/**
 * Adda implements simple publish/subscribe for objects sent via Akka Streams.
 *
 * The handlers in `privilegedHandlers' get called on all published entities
 * and they are guaranteed to finish running before the entity is passed on to the subscribers.
 *
 * Adda automatically completes all sources for a class, when the number of tracked publishers
 * for this class was > 0, and then falls back to 0 due to stream completions.
 *
 * PubSub cycles are possible, but in this case the stream completion propagation does not work.
 */
class Adda(
  private[this] val privilegedHandlers: List[Any => Unit] = Nil,
  implicit val system: ActorSystem = ActorSystem(Adda.defaultSystemName)) extends PubSub {

  private[this] val broadcasterForTopic = collection.mutable.Map.empty[String, ActorRef]

  implicit val materializer = ActorFlowMaterializer()
  implicit val executor = system.dispatcher
  private[this] val log = Logging.getLogger(system.eventStream, Adda.defaultSystemName)

  def subscribe[C: ClassTag]: Source[C, Unit] = {
    val publisher = createPublisher[C]
    val source = Source(publisher)
    source
  }

  def publish[C: ClassTag](trackCompletion: Boolean = true): Sink[C, Unit] = {
    val subscriber = createSubscriber[C](trackCompletion)
    val sink = Sink(subscriber)
    sink
  }

  def awaitCompleted(implicit timeout: Timeout = Timeout(300.seconds)): Unit = {
    val completedFuture = Future.sequence(broadcasterForTopic.values.map(b => b ? AwaitCompleted))
    Await.result(completedFuture, timeout.duration)
  }

  def shutdown(): Unit = {
    log.debug("Shutting down Adda ...")
    system.shutdown()
    system.awaitTermination()
    log.debug("Adda has shut down.")
  }

  private[this] def topic[C: ClassTag]: String = {
    implicitly[ClassTag[C]].runtimeClass.getName
  }

  private[this] def broadcaster(topic: String): ActorRef = {
    broadcasterForTopic.get(topic) match {
      case Some(b) => b
      case None    => createBroadcasterForTopic(topic)
    }
  }

  private[this] def createBroadcasterForTopic(topic: String): ActorRef = {
    val broadcaster = system.actorOf(Props(new Broadcaster(privilegedHandlers)), topic)
    broadcasterForTopic += topic -> broadcaster
    broadcaster
  }

  private[this] def createPublisher[C: ClassTag]: Publisher[C] = {
    implicit val timeout = Timeout(5.seconds)
    val t = topic[C]
    val b = broadcaster(t)
    val publisherActorFuture = b ? CreatePublisher[C]()
    val publisherFuture = publisherActorFuture.map(p => ActorPublisher[C](p.asInstanceOf[ActorRef]))
    Await.result(publisherFuture, 5.seconds)
  }

  private[this] def createSubscriber[C: ClassTag](trackCompletion: Boolean): Subscriber[C] = {
    implicit val timeout = Timeout(5.seconds)
    val t = topic[C]
    val b = broadcaster(t)
    val subscriberActorFuture = b ? CreateSubscriber(trackCompletion)
    val subscriberFuture = subscriberActorFuture.map(s => ActorSubscriber[C](s.asInstanceOf[ActorRef]))
    Await.result(subscriberFuture, 5.seconds)
  }

}

object Adda {
  val defaultSystemName = "Adda"
}
