package com.adda

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag
import org.reactivestreams.{ Publisher, Subscriber }
import com.adda.interfaces.PubSub
import com.adda.pubsub.{ AwaitCompleted, Broadcaster, CreatePublisher, CreateSubscriber }
import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.pattern.ask
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.{ ActorPublisher, ActorSubscriber }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout
import scala.concurrent.Future
import akka.event.Logging

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
  private[this] implicit val system: ActorSystem = ActorSystem("Adda")) extends PubSub {

  private[this] var broadcasterForTopic = Map.empty[String, ActorRef]

  implicit val materializer = ActorFlowMaterializer()
  import system.dispatcher
  private[this] val log = Logging.getLogger(system.eventStream, "Adda")

  /**
   * Returns an Akka Streams source that is subscribed to all published objects of class `C'.
   * Only returns exact instances of `C' and no subclasses.
   */
  def getSource[C: ClassTag]: Source[C, Unit] = {
    val publisher = getPublisher[C]
    val source = Source(publisher)
    source
  }

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   * Only allows to publish exact instances of `C' and no subclasses.
   *
   * When a GraphSerializable object is streamed into this sink, then the triples of that object are
   * published to the triple store before the object is published to any of the subscribers.
   */
  def getSink[C: ClassTag]: Sink[C, Unit] = {
    val subscriber = getSubscriber[C]()
    val sink = Sink(subscriber)
    sink
  }

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   *
   * The difference to `getSink' is that this sink is expected to complete soon,
   * and will never propagate the completion to the sources that subscribe to the class.
   */
  def getTemporarySink[C: ClassTag]: Sink[C, Unit] = {
    val subscriber = getSubscriber[C](isTemporary = true)
    val sink = Sink(subscriber)
    sink
  }

  /**
   * Blocking call that returns once all the incoming sinks have completed and all the sources
   * have published the remaining items.
   *
   * Adda automatically completes all sources for a class, when the number of active sinks
   * for this class was > 0, and then falls back to 0.
   */
  def awaitCompleted()(implicit timeout: Timeout = Timeout(60.seconds)): Unit = {
    val completedFuture = Future.sequence(broadcasterForTopic.values.map(b => b ? AwaitCompleted))
    Await.result(completedFuture, timeout.duration)
  }

  /**
   * Blocking call that shuts down Adda and returns when the shutdown is completed.
   */
  def shutdown(): Unit = {
    log.warning("Shutting down Adda")
    system.shutdown()
    system.awaitTermination()
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

  private[this] def getPublisher[C: ClassTag]: Publisher[C] = {
    implicit val timeout = Timeout(5.seconds)
    val t = topic[C]
    val b = broadcaster(t)
    val publisherActorFuture = b ? CreatePublisher[C]()
    val publisherFuture = publisherActorFuture.map(p => ActorPublisher[C](p.asInstanceOf[ActorRef]))
    Await.result(publisherFuture, 5.seconds)
  }

  private[this] def getSubscriber[C: ClassTag](isTemporary: Boolean = false): Subscriber[C] = {
    implicit val timeout = Timeout(5.seconds)
    val t = topic[C]
    val b = broadcaster(t)
    val subscriberActorFuture = b ? CreateSubscriber(isTemporary)
    val subscriberFuture = subscriberActorFuture.map(s => ActorSubscriber[C](s.asInstanceOf[ActorRef]))
    Await.result(subscriberFuture, 5.seconds)
  }

}
