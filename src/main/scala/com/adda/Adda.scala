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
import com.adda.pubsub.CreatePublisher
import com.adda.pubsub.SinkActor

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.pattern.ask
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorSubscriber
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.Timeout

class Adda extends PubSub with SparqlSelect {

  private[this] val store: TripleStore = new SesameAdapter
  implicit val system: ActorSystem = ActorSystem("Adda")
  private[this] implicit val materializer = ActorFlowMaterializer()
  private[this] val broadcastActor = system.actorOf(Props(new BroadcastActor()))

  /**
   * Executes SPARQL select query `query'.
   *
   * @return an iterator of query results.
   */
  def executeSparqlSelect(query: String): Iterator[String => String] = {
    store.executeSparqlSelect(query)
  }

  def subscribeToSource[C: ClassTag]: Source[C] = {
    val publisher = createPublisher[C]
    val source = Source(publisher)
    source
  }

  def getPublicationSink[C]: Sink[C] = {
    val subscriber = createSubscriber[C]
    val sink = Sink(subscriber)
    sink
  }

  private[this] def createPublisher[C: ClassTag]: Publisher[C] = {
    import system.dispatcher
    implicit val timeout = Timeout(5.seconds)
    val publisherActorFuture = broadcastActor ? CreatePublisher[C]()
    val publisherFuture = publisherActorFuture.map(p => ActorPublisher[C](p.asInstanceOf[ActorRef]))
    Await.result(publisherFuture, 5.seconds)
  }

  private[this] def createSubscriber[C]: Subscriber[C] = {
    val subscriberActor = system.actorOf(Props(new SinkActor(broadcastActor)))
    val subscriber = ActorSubscriber[C](subscriberActor)
    subscriber
  }

}
