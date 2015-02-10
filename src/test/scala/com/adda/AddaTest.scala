package com.adda

/**
 * Created by asingh on 10/02/15.
 */

import akka.stream.scaladsl.{Sink, Source}
import org.scalatest._

class AddaTest extends FlatSpec {
  "Adda" should "create Source of appropriate type" in {
    val adda = new Adda
    val source = adda.subscribeToSource[List[String]]
    assert(source.isInstanceOf[Source[List[String]]])
  }

  "Adda" should "create Sink of appropriate type" in {
    val adda = new Adda
    val sink = adda.getPublicationSink[String]
    assert(sink.isInstanceOf[Sink[String]])
  }

}
