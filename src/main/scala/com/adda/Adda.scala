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
import com.adda.pubsub.BroadcastActor
import com.adda.pubsub.CompleteAllPublishers
import com.adda.pubsub.CreatePublisher
import com.adda.pubsub.SinkActor

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.pattern.ask
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorSubscriber
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.Timeout

/**
 * Adda implements simple publish/subscribe for objects sent via Akka Streams.
 * It also exposes a SPARQL API for a triple store.
 *
 * When a GraphSerializable object is published to Adda, then the triples of that entity are
 * published to the triple store before the entity is published to any of the Adda subscribers.
 */
class Adda extends PubSub with SparqlSelect {

  private[this] val store: TripleStore = new SesameAdapter
  private[this] implicit val system: ActorSystem = ActorSystem("Adda")
  private[this] implicit val materializer = ActorFlowMaterializer()
  private[this] val broadcastActor = system.actorOf(Props(new BroadcastActor(store)))

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
   */
  def getSource[C: ClassTag]: Source[C] = {
    val publisher = getPublisher[C]
    val source = Source(publisher)
    source
  }

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   */
  def getSink[C]: Sink[C] = {
    val subscriber = getSubscriber[C]
    val sink = Sink(subscriber)
    sink
  }

  /**
   * Blocking call that returns once the pub/sub infrastructure has shut down.
   * Shutdown closes all stream sources.
   */
  def shutdown() {
    broadcastActor ! CompleteAllPublishers
    system.shutdown()
    system.awaitTermination()
  }

  private[this] def getPublisher[C: ClassTag]: Publisher[C] = {
    import system.dispatcher
    implicit val timeout = Timeout(5.seconds)
    val publisherActorFuture = broadcastActor ? CreatePublisher[C]()
    val publisherFuture = publisherActorFuture.map(p => ActorPublisher[C](p.asInstanceOf[ActorRef]))
    Await.result(publisherFuture, 5.seconds)
  }

  private[this] def getSubscriber[C]: Subscriber[C] = {
    val subscriberActor = system.actorOf(Props(new SinkActor(broadcastActor)))
    val subscriber = ActorSubscriber[C](subscriberActor)
    subscriber
  }

}
