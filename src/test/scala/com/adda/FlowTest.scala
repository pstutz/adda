package com.adda

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Broadcast
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.FlowGraph
import akka.stream.scaladsl.FlowGraphImplicits.JunctionOps
import akka.stream.scaladsl.FlowGraphImplicits.SourceOps
import akka.stream.scaladsl.Source

// TODO: Turn into ScalaTest.
object FlowTest extends App {
  val adda = new Adda
  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorFlowMaterializer()

  val listHandlingApp: Flow[List[String], String] = Flow[List[String]].
    map { list =>
      val asString = list.mkString("-")
      println(s"App handled list: $asString")
      asString
    }

  Source(List(List("cptCode -> A2015", "dos -> 20140201")))
    .runWith(adda.getPublicationSink[List[String]])

  adda.subscribeToSource[List[String]]
    .via(listHandlingApp)
    .runWith(adda.getPublicationSink[String])

  val stringHandlingApp: Flow[String, Int] = Flow[String].map(f => 1)

  adda.subscribeToSource[String]
    .via(stringHandlingApp)
    .runWith(adda.getPublicationSink[Int])

  shutdown()

  def shutdown() {
    adda.shutdown()
    system.shutdown()
  }
}

// TODO: Turn into ScalaTest.
object FlowTestWithBcast extends App {
  val adda = new Adda
  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorFlowMaterializer()

  val listHandlingApp: Flow[List[String], String] = Flow[List[String]].
    map { list =>
      val asString = list.mkString("-")
      println(s"App handled list: $asString")
      asString
    }

  val listHandlingAppToInt: Flow[List[String], Int] = Flow[List[String]].map(f => 1)


  val materializedFlow = FlowGraph { implicit builder =>
    import akka.stream.scaladsl.FlowGraphImplicits._
    val bcast = Broadcast[List[String]]
    Source(List(List("cptCode -> A2015", "dos -> 20140201"))) ~> bcast ~> listHandlingApp ~> adda.getPublicationSink[String]
    bcast ~> listHandlingAppToInt ~> adda.getPublicationSink[Int]

  }.run()

}
