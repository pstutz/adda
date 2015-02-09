package com.adda

import com.adda.adapters.SesameAdapter
import com.adda.interfaces.GraphSerializable
import com.adda.interfaces.PubSub
import com.adda.interfaces.SparqlSelect
import com.adda.interfaces.TripleStore
import akka.actor.{ActorRef, Actor, ActorSystem, Props}
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Source
import akka.stream.ActorFlowMaterializer
import com.adda.entities.Pricer
import com.adda.entities.GeneralSource
import akka.stream.actor.ActorSubscriber
import com.adda.entities.ClaimLine
import akka.stream.actor.ActorPublisher
import com.adda.entities.GeneralSink
import akka.stream.scaladsl.FlowGraphImplicits
import akka.stream.scaladsl.FlowGraph
import akka.stream.scaladsl.Flow
import com.adda.entities.AddFeedPublisher
import scala.reflect.ClassTag

class Adda extends PubSub with SparqlSelect {

  private[this] val store: TripleStore = new SesameAdapter
  private[this] implicit val system: ActorSystem = ActorSystem("Adda")
  private[this] implicit val materializer = ActorFlowMaterializer()

  private[this] val subscriberActor = system.actorOf(Props(new GeneralSink()))
  private[this] val subscriber = ActorSubscriber[Any](subscriberActor)

  /**
   * Executes SPARQL select query `query'.
   *
   * @return an iterator of query results.
   */
  def executeSparqlSelect(query: String): Iterator[String => String] = {
    store.executeSparqlSelect(query)
  }

  def subscribeToSource[C: ClassTag](c: Class[C]): Source[C] = {
    val publisherActor = system.actorOf(Props[GeneralSource])
    val publisher = ActorPublisher[AnyRef](publisherActor)
    val clazz: Class[_] = implicitly[ClassTag[C]].runtimeClass
    subscriberActor ! AddFeedPublisher(publisherActor)
    val source = Source(publisher)
      .filter { entity => clazz.isAssignableFrom(entity.getClass) }
      .map { _.asInstanceOf[C] }
    source
  }

  def getPublicationSink[C]: Sink[C] = {
    Sink.apply(subscriber)
  }

  def getPublicationSink[C](actorSubscriber: ActorRef) = {
    val subscriber = ActorSubscriber[Any](actorSubscriber)
    Sink.apply(subscriber)
  }

}
