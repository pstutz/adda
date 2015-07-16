package com.ihealthtechnologies.adda.integration

import java.util.concurrent.atomic.AtomicInteger
import org.scalatest.{ Finders, FlatSpec, Matchers }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe
import com.ihealthtechnologies.adda.Adda

class HandlerTest extends FlatSpec with Matchers {

  "A privileged handler" should "receive all published entities" in {

    val counter = new AtomicInteger

    def intHandler(e: Any) = {
      e match {
        case i: Int => counter.addAndGet(i)
        case _      => throw new Exception(s"Handler received an unexpected message.")
      }
    }

    val adda = new Adda(List(intHandler))
    implicit val system = adda.system
    implicit val materializer = adda.materializer

    val elements = 1000000

    val probe = manualProbe[Int]
    val in = Source(1 to elements).to(adda.publish[Int])
    val out = adda.subscribe[Int].to(Sink(probe))
    out.run
    in.run

    probe.expectSubscription().request(elements)
    for { i <- 1 to elements } {
      probe.expectNext(i)
    }
    probe.expectComplete()

    val entrySum = (1 to elements).sum
    counter.get should be(entrySum)

    adda.awaitCompleted()
    adda.shutdown()
  }

}