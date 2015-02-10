package com.adda.adapters

import scala.util.Failure
import scala.util.Success
import org.scalatest.BeforeAndAfterAll
import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.Matchers
import akka.actor.ActorSystem
import akka.stream.FlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source
import akka.stream.scaladsl.StreamTcp
import akka.util.ByteString
import com.adda.interfaces.TripleStore

class AdapterTest extends FlatSpec with Matchers {

  "SesameAdapter" should "answer a simple SPARQL query" in {
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

    val tripleStore: TripleStore = new SesameAdapter
    tripleStore.addTriple(s"$acme#Sam", s"$foaf/name", name)
    tripleStore.addTriple(s"$acme#Sam", s"$foaf/mbox", mail)
    val results = tripleStore.executeSparqlSelect(query).toList
    results.size should be(1)
    val result = results.head
    result("name") should be(name)
    result("email") should be(mail)
  }

}
