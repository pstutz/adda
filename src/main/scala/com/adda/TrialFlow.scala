package com.adda

import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source

object TrialFlow extends App {
  val adda = new Adda
  implicit val system = adda.system
  implicit val materializer = ActorFlowMaterializer()

  val listHandlingApp: Flow[List[String], String] = Flow[List[String]].
    map { list =>
      val asString = list.mkString("-")
      println(s"App handled list: $asString")
      asString
    }

  adda.subscribeToSource[List[String]]
    .via(listHandlingApp)
    .runWith(adda.getPublicationSink[String])

  Source(List(List("cptCode -> A2015", "dos -> 20140201")))
    .runWith(adda.getPublicationSink[List[String]])
}
