package com.ihtech.adda

import java.util.concurrent.atomic.AtomicInteger

import org.scalatest.{ FlatSpec, Matchers }

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.StreamTestKit

class HandlerTest extends FlatSpec with Matchers {

  "A privileged handler" should "receive all published entities" in {

    val counter = new AtomicInteger
    def intHandler(e: Any) = {
      e match {
        case i: Int => counter.addAndGet(i)
        case other  => throw new Exception("Unexpected entry.")
      }
    }

    val adda = new Adda(List(intHandler))
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorFlowMaterializer()

    val elements = 1000000

    val probe = StreamTestKit.SubscriberProbe[Int]
    val in = Source(1 to elements).to(adda.publish[Int])
    val out = adda.subscribe[Int].to(Sink(probe))
    out.run
    in.run

    probe.expectSubscription().request(elements)
    for (i <- 1 to elements) {
      probe.expectNext(i)
    }
    probe.expectComplete()

    val entrySum = (1 to elements).sum
    counter.get should be(entrySum)

    adda.awaitCompleted()
    adda.shutdown()
  }

}
