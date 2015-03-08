package com.adda

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

object ThroughputTest extends App {

  val adda = new Adda
  implicit val materializer = adda.materializer
  val messageCountingSink = Sink.fold[Int, Any](0) { case (aggr, next) => aggr + 1 }

  val elements = 10000000
  val startTime = System.currentTimeMillis
  val processed = adda.getSource[Int].runWith(messageCountingSink)
  Source(1 to elements).to(adda.getSink[Int]).run
  val actualProcessed = Await.result(processed, 10000.seconds)
  val finishTime = System.currentTimeMillis
  val delta = finishTime - startTime
  println(s"Processed $actualProcessed messages in $delta milliseconds.")

  adda.shutdown
}
