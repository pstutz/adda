package com.adda

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.adda.interfaces.GraphSerializable
import com.adda.interfaces.Triple

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source

case class TripleContainer(asGraph: List[Triple]) extends GraphSerializable

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

    val nameKey = "name"
    val mailKey = "email"

    Source(List(triples))
      .runWith(adda.getSink[TripleContainer])

    val queryApp: Flow[GraphSerializable, List[String => String]] = Flow[GraphSerializable]
      .map(_ => adda.executeSparqlSelect(query).toList)

    val bindingsListFuture: Future[List[String => String]] = adda.getSource[TripleContainer]
      .via(queryApp)
      .runFold(List.empty[String => String]) { case (aggr, next) => aggr ::: next }

    Thread.sleep(8000)
    adda.shutdown

    val bindingsList = Await.result(bindingsListFuture, 5.seconds)

    bindingsList.size should be(1)
    val bindings = bindingsList.head

    val resultName = bindings(nameKey)
    val resultMail = bindings(mailKey)
    resultName should be(name)
    resultMail should be(mail)

  }

}
