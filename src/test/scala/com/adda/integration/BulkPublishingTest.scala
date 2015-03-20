package com.adda.integration

import org.scalatest.{ FlatSpec, Matchers }

import com.adda.Adda

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.StreamTestKit

class BulkPublishingTest extends FlatSpec with Matchers {

  "Adda" should "deliver bulked messages in the correct order" in {
    val adda = new Adda
    implicit val system = adda.system
    implicit val materializer = adda.materializer

    val maxElements = 1000000
    val ascendingInts = 1 to maxElements

    val probe = StreamTestKit.SubscriberProbe[Int]
    val in = Source(ascendingInts).to(adda.createSink[Int])
    val out = adda.createSource[Int].to(Sink(probe))
    out.run
    in.run

    probe.expectSubscription().request(maxElements)
    for { i <- ascendingInts } {
      probe.expectNext(i)
    }
    probe.expectComplete()
    adda.awaitCompleted()
    adda.shutdown()
  }
}
