package com.ihtech.adda.integration

import org.scalatest.{ Finders, FlatSpec, Matchers }

import com.ihtech.adda.Adda

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe

class BulkPublishingTest extends FlatSpec with Matchers {

  "Adda" should "deliver bulked messages in the correct order" in {
    val adda = new Adda
    implicit val system = adda.system
    implicit val materializer = adda.materializer

    val maxElements = 1000000
    val ascendingInts = 1 to maxElements

    val probe = manualProbe[Int]
    adda.subscribe[Int].to(Sink(probe)).run
    Source(ascendingInts).to(adda.publish[Int]).run

    probe.expectSubscription().request(maxElements)
    for { i <- ascendingInts } {
      probe.expectNext(i)
    }
    probe.expectComplete()
    adda.awaitCompleted()
    adda.shutdown()
  }

}
