package com.adda.adapters

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.adda.interfaces.Triple
import com.adda.interfaces.TripleStore

class AdapterTest extends FlatSpec with Matchers {

  "SesameAdapter" should "answer a simple SPARQL query" in {
    val acme = "http://acme.com/people#"
    val foaf = "http://xmlns.com/foaf/0.1"
    val name = "Sam"
    val mail = "sam@acme.com"
    val url = s"$acme/someUrl"

    val query = s"""
PREFIX foaf:  <$foaf/>
PREFIX acme:  <$acme#>
SELECT *
WHERE {
    ?person foaf:name ?name .
    ?person foaf:mbox ?email .
    ?person acme:somePredicate ?url .
}
"""
    val samIri = s"$acme#Sam"

    val tripleStore: TripleStore = new SesameAdapter
    try {
      tripleStore.addTriple(Triple(samIri, s"$foaf/name", name))
      tripleStore.addTriples(List(
        Triple(samIri, s"$foaf/mbox", mail),
        Triple(samIri, s"$acme#somePredicate", url)).iterator)
      val results = tripleStore.executeSparqlSelect(query).toList
      results.size should be(1)
      val result = results.head
      result("name") should be(name)
      result("email") should be(mail)
      result("url") should be(url)
    } finally {
      tripleStore.shutdown()
    }
  }

}
