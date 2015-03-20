package com.ihtech.adda

import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

import akka.stream.scaladsl.{ Sink, Source }

object ThroughputTest extends App {

  val adda = new Adda
  implicit val materializer = adda.materializer
  val messageCountingSink = Sink.fold[Int, Any](0) { case (aggr, next) => aggr + 1 }

  val elements = 10000000
  val startTime = System.currentTimeMillis
  val processed = adda.subscribe[Int].runWith(messageCountingSink)
  Source(1 to elements).to(adda.publish[Int]).run
  val actualProcessed = Await.result(processed, 10000.seconds)
  val finishTime = System.currentTimeMillis
  val delta = finishTime - startTime
  println(s"Processed $actualProcessed messages in $delta milliseconds.")

  adda.shutdown
}
