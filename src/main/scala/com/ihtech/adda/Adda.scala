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
 * It also exposes a SPARQL API for a triple store.
 *
 * The handlers in `privilegedHandlers' get called on all entities that Adda receives,
 * and they are guaranteed to finish running before the entity is passed on to the sinks.
 *
 * Adda automatically completes all sources for a class, when the number of active non-temporary sinks
 * for this class was > 0, and then falls back to 0.
 *
 * PubSub cycles are possible, but in this case the automated stream completion does
 * not work.
 */
class Adda(
  private[this] val privilegedHandlers: List[Any => Unit] = Nil,
  implicit val system: ActorSystem = ActorSystem(Adda.defaultSystemName)) extends PubSub {

  private[this] val broadcasterForTopic = collection.mutable.Map.empty[String, ActorRef]

  implicit val materializer = ActorFlowMaterializer()
  implicit val executor = system.dispatcher
  private[this] val log = Logging.getLogger(system.eventStream, Adda.defaultSystemName)

  /**
   * Returns an Akka Streams source that is subscribed to all published objects of class `C'.
   * Only returns exact instances of `C' and no subclasses.
   */
  def subscribe[C: ClassTag]: Source[C, Unit] = {
    val publisher = createPublisher[C]
    val source = Source(publisher)
    source
  }

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   *
   * The `trackCompletion' parameter determines if the pubsub system should track the completion of this publisher.
   *
   * The pubsub system completes all subscribers for a topic, when the number of tracked publishers
   * for this class was > 0, and then falls back to 0.
   */
  def publish[C: ClassTag](trackCompletion: Boolean = true): Sink[C, Unit] = {
    val subscriber = createSubscriber[C](trackCompletion)
    val sink = Sink(subscriber)
    sink
  }

  /**
   * Blocking call that returns once all the publishers and subscribers have completed.
   *
   * The pubsub system completes all subscribers for a topic, when the number of tracked publishers
   * for this class was > 0, and then falls back to 0.
   */
  def awaitCompleted(implicit timeout: Timeout = Timeout(300.seconds)): Unit = {
    val completedFuture = Future.sequence(broadcasterForTopic.values.map(b => b ? AwaitCompleted))
    Await.result(completedFuture, timeout.duration)
  }

  /**
   * Blocking call that shuts down Adda and returns when the shutdown is completed.
   */
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
