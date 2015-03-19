package com.adda.integration

import org.scalatest.{ Finders, FlatSpec, Matchers }

import com.adda.Adda

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.StreamTestKit

class BulkPublishingTest extends FlatSpec with Matchers {

  "Adda" should "deliver bulked messages in the correct order" in {

    val adda = new Adda
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorFlowMaterializer()

    val elements = 1000000

    val probe = StreamTestKit.SubscriberProbe[Int]
    val in = Source(1 to elements).to(adda.createSink[Int])
    val out = adda.createSource[Int].to(Sink(probe))
    out.run
    in.run

    probe.expectSubscription().request(elements)
    for (i <- 1 to elements) {
      probe.expectNext(i)
    }
    probe.expectComplete()

    adda.awaitCompleted()
    adda.shutdown()

  }
}
