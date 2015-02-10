package com.adda

import org.scalatest.FlatSpec

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

class AddaTest extends FlatSpec {

  // TODO(akanksha): This test cannot fail, because the generic type parameter List[String] is erased at runtime. 
  "Adda" should "create Source of appropriate type" in {
    val adda = new Adda
    val source = adda.subscribeToSource[List[String]]
    assert(source.isInstanceOf[Source[List[String]]])
  }

  // TODO(akanksha): This test cannot fail, because the generic type parameter String is erased at runtime.
  "Adda" should "create Sink of appropriate type" in {
    val adda = new Adda
    val sink = adda.getPublicationSink[String]
    assert(sink.isInstanceOf[Sink[String]])
  }

  "Adda" should "be able to run sparql query" in {
    val adda = new Adda
    val query =
      """
        |SELECT ?sub ?pred ?obj
        |WHERE
        |{
        |?sub ?pred ?obj .
        |}
        |LIMIT 1
      """.stripMargin
    assert(adda.executeSparqlSelect(query).isInstanceOf[Iterator[String => String]])
  }

}
