package com.adda

import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source

import scala.concurrent.Future

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

  Source(List(List("cptCode -> A2015", "dos -> 20140201")))
    .runWith(adda.getPublicationSink[List[String]])
  
  adda.subscribeToSource[List[String]]
    .via(listHandlingApp)
    .runWith(adda.getPublicationSink[String])

  val stringHandlingApp: Flow[String, Int] = Flow[String].map(f => 1)

  adda.subscribeToSource[String]
      .via(stringHandlingApp)
      .runWith(adda.getPublicationSink[Int])

}


object TrialFlowWithHttp extends App {
  val adda = new Adda
  implicit val system = adda.system
  implicit val materializer = ActorFlowMaterializer()
  val serverBinding2 = Http().bind(interface = "localhost", port = 8091)
  serverBinding2.connections.runForeach {
    connection =>
      connection.handleWith(Flow[HttpRequest].mapAsync(asyncHandler))
  }

  def asyncHandler(request: HttpRequest): Future[HttpResponse] = {
    case HttpRequest(_, Uri.Path("/claim"), _, _, _) => {
      Future[HttpResponse] {
        HttpResponse(entity = "claim line accepted",status = StatusCodes.OK)
      }
    }
    
    case HttpRequest(_, Uri.Path("/price"), _, _, _) => {
        Future[HttpResponse] {
          HttpResponse(entity = "pricing for claim line done",status = StatusCodes.OK)
        }
    }
  }


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

}
