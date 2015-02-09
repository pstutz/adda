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
import com.adda.entities._
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorPublisher
import akka.stream.scaladsl.FlowGraphImplicits
import akka.stream.scaladsl.FlowGraph
import akka.stream.scaladsl.Flow
import scala.reflect.ClassTag

class Adda extends PubSub with SparqlSelect {

  private[this] val store: TripleStore = new SesameAdapter
  implicit val system: ActorSystem = ActorSystem("Adda")
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

  def subscribeToSource[C: ClassTag]: Source[C] = {
    val publisherActor = system.actorOf(Props[GeneralSource])
    val publisher = ActorPublisher[AnyRef](publisherActor)
    val clazz: Class[_] = implicitly[ClassTag[C]].runtimeClass
    subscriberActor ! AddFeedPublisher(publisherActor)
    val source = Source(publisher)
      .filter { entity => clazz.isAssignableFrom(entity.getClass) }
      .map { _.asInstanceOf[C] }
    source
  }

  def subscribeToSource[C: ClassTag](actorPublisher: ActorRef): Source[C] = {
    val publisher = ActorPublisher[AnyRef](actorPublisher)
    val clazz: Class[_] = implicitly[ClassTag[C]].runtimeClass
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

object TrialFlowUsingAdda extends App {
  val adda = new Adda
  implicit val system = adda.system
  implicit val materializer = ActorFlowMaterializer()
  val publisherActor = system.actorOf(Props[GeneralSource])
  publisherActor ! AddaEntity(ClaimLine(Map("cptCode" -> "A2015", "dos" -> "20140201")))
  publisherActor ! AddaEntity(Recommendation(Map("cptCode" -> "A2015", "dos" -> "20140201", "recommendedCptCode" -> "A2195")))
  val subscriberActor = system.actorOf(Props(new Pricer(5)))
  adda.subscribeToSource[ClaimLine](publisherActor).runWith(adda.getPublicationSink(subscriberActor))(materializer)
}