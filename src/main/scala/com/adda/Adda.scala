package com.adda

import com.adda.adapters.SesameAdapter
import com.adda.interfaces.GraphSerializable
import com.adda.interfaces.PubSub
import com.adda.interfaces.SparqlSelect
import com.adda.interfaces.TripleStore
import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Source
import akka.stream.ActorFlowMaterializer

class Adda extends PubSub with SparqlSelect {

  private[this] val store: TripleStore = new SesameAdapter
  private[this] implicit val system: ActorSystem = ActorSystem("Adda")

  implicit val materializer = ActorFlowMaterializer()

  /**
   * Executes SPARQL select query `query'.
   *
   * @return an iterator of query results.
   */
  def executeSparqlSelect(query: String): Iterator[String => String] = {
    store.executeSparqlSelect(query)
  }

  private[this] val universalBroadcast = Broadcast[AnyRef]

  def subscribeToSource[C](c: Class[C]): Source[C] = {
    universalBroadcast.filter(_.isInstanceOf[C])
  }

  def getPublicationSink[C]: Sink[C] = ???

  def getGraphPublicationSink[C <: GraphSerializable]: Sink[C] = ???

}
