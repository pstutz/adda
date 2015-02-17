package com.adda

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber

import com.adda.adapters.SesameAdapter
import com.adda.interfaces.PubSub
import com.adda.interfaces.SparqlSelect
import com.adda.interfaces.TripleStore
import com.adda.pubsub._

import akka.actor.{Props, ActorRef, ActorSystem}
import akka.pattern.ask
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorSubscriber
import akka.stream.scaladsl.{PropsSource, Sink, Source}
import akka.util.Timeout

/**
 * Adda implements simple publish/subscribe for objects sent via Akka Streams.
 * It also exposes a SPARQL API for a triple store.
 *
 * When a GraphSerializable object is published to Adda, then the triples of that object are
 * published to the triple store before the object is published to any of the Adda subscribers.
 *
 * Adda automatically completes all sources for a class, when the number of active sinks
 * for this class was > 0, and then falls back to 0.
 *
 * PubSub cycles are possible, but in this case the automated stream completion does
 * not work.
 */
class Adda extends PubSub with SparqlSelect {

  private[this] val store: TripleStore = new SesameAdapter
  private[this] implicit val system: ActorSystem = ActorSystem("Adda")
  private[this] implicit val materializer = ActorFlowMaterializer()
  private[this] val broadcastActor = system.actorOf(Props(new BroadcastActor(store)), "broadcast")
  import system.dispatcher

  /**
   * Executes SPARQL select query `query'.
   *
   * TODO: Explain the connection between GraphSerializable, publishing objects, and the triple store.
   *
   * @return an iterator of query results.
   */
  def executeSparqlSelect(query: String): Iterator[String => String] = {
    store.executeSparqlSelect(query)
  }

  /**
   * Returns an Akka Streams source that is subscribed to all published objects of class `C'.
   * Only returns exact instances of `C' and no subclasses.
   */
  def getSource[C: ClassTag]: Source[C] = {
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
  def getSink[C: ClassTag]: Sink[C] = {
    val subscriber = getSubscriber[C]
    val sink = Sink(subscriber)
    sink
  }

  /**
   * Returns an Akka stream that is used as a buffer for incoming http requests of class `C'.
   */
  def getHttpSource[C: ClassTag]: PropsSource[C] = {
    Source[C](Props[SourceActor[C]])
  }

  /**
   * Blocking call that returns once all the incoming sinks have completed and all the sources
   * have published the remaining items.
   *
   * Adda automatically completes all sources for a class, when the number of active sinks
   * for this class was > 0, and then falls back to 0.
   */
  def awaitCompleted()(implicit timeout: Timeout = Timeout(60.seconds)) {
    val completedFuture = broadcastActor ? AwaitCompleted
    Await.result(completedFuture, timeout.duration)
  }

  /**
   * Blocking call that shuts down Adda and returns when the shutdown is completed.
   */
  def shutdown() {
    system.shutdown()
    system.awaitTermination()
  }

  private[this] def getPublisher[C: ClassTag]: Publisher[C] = {
    implicit val timeout = Timeout(5.seconds)
    val publisherActorFuture = broadcastActor ? CreatePublisher[C]()
    val publisherFuture = publisherActorFuture.map(p => ActorPublisher[C](p.asInstanceOf[ActorRef]))
    Await.result(publisherFuture, 5.seconds)
  }

  private[this] def getSubscriber[C: ClassTag]: Subscriber[C] = {
    val className = implicitly[ClassTag[C]].runtimeClass.getName
    val subscriberActor = system.actorOf(Props(new SinkActor(broadcastActor, className)))
    val subscriber = ActorSubscriber[C](subscriberActor)
    subscriber
  }

}
