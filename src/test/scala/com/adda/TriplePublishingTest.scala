package com.adda

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.adda.interfaces.GraphSerializable
import com.adda.messages.Triple

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

case class TripleContainer(asGraph: List[Triple]) extends GraphSerializable

// TODO: Fix test.
class TriplePublishingTest extends FlatSpec with Matchers {

  "Adda" should "answer a simple SPARQL query, when the triples were published before" in {
    val query = """
PREFIX foaf:  <http://xmlns.com/foaf/0.1/>
SELECT *
WHERE {
    ?person foaf:name ?name .
    ?person foaf:mbox ?email .
}
"""
    val acme = "http://acme.com/people"
    val foaf = "http://xmlns.com/foaf/0.1"
    val name = "Sam"
    val mail = "sam@acme.com"

    val triples = TripleContainer(List(
      Triple(s"$acme#Sam", s"$foaf/name", name),
      Triple(s"$acme#Sam", s"$foaf/mbox", mail)))

    val adda = new Adda
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorFlowMaterializer()

    val queryApp = Flow[GraphSerializable]
      .map { gs =>
        println("MOOOO!")
        val results = adda.executeSparqlSelect(query).toList
        results.size should be(1)
        val result = results.head
        result("name") should be("Sa")
        result("email") should be(mail)
        ()
      }

    adda.subscribeToSource[TripleContainer]
      .via(queryApp)
      .runWith(Sink.ignore)

    Source(List(triples))
      .runWith(adda.getPublicationSink[TripleContainer])

  }

}
