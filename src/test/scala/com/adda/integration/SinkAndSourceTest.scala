package com.adda.integration

import scala.reflect.ClassTag

import org.scalacheck.Arbitrary.arbContainer
import org.scalacheck.Prop.propBoolean
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.Checkers

import com.adda.Adda
import com.adda.TestingConstants.{ probeMinItemsRequested, successfulTest }

import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.AkkaSpec
import akka.stream.testkit.StreamTestKit.SubscriberProbe

class SinkAndSourceTest extends AkkaSpec with Checkers with ScalaFutures {
  implicit val materializer = ActorFlowMaterializer()

  /**
   * Verifies that a sink receives the elements in `l', when they are streamed into Adda by a source.
   */
  def verifySingleSinkAndSourceFlow[C: ClassTag](l: List[C], adda: Adda): Boolean = {
    val probe = SubscriberProbe[C]
    adda.getSource[C].to(Sink(probe)).run
    Source(l).to(adda.getSink[C]).run
    verifyWithProbe(l, probe)
    adda.awaitCompleted
    successfulTest
  }

  /**
   * Verifies the probe receives the items in `l' and that the stream completes afterwards.
   */
  def verifyWithProbe[C](l: List[C], probe: SubscriberProbe[C]) {
    val itemsRequested = math.max(probeMinItemsRequested, l.length)
    probe.expectSubscription().request(itemsRequested)
    for (next <- l) {
      probe.expectNext(next)
    }
    probe.expectComplete
  }

  "Adda" must {

    "support single publisher, single subscriber pubsub for strings" in {
      val adda = new Adda
      check((elements: List[String]) => verifySingleSinkAndSourceFlow(elements, adda))
      adda.shutdown
    }

    "support single publisher, single subscriber pubsub for ints" in {
      val adda = new Adda
      check((elements: List[Int]) => verifySingleSinkAndSourceFlow(elements, adda))
      adda.shutdown
    }

    "support single publisher, single subscriber pubsub for double" in {
      val adda = new Adda
      check((elements: List[Double]) => verifySingleSinkAndSourceFlow(elements, adda))
      adda.shutdown
    }

    "support single publisher, multiple subscriber pubsub for strings" in {
      val adda = new Adda
      check { (elements: List[String]) =>
        val probeA = SubscriberProbe[String]
        val probeB = SubscriberProbe[String]
        adda.getSource[String].to(Sink(probeA)).run
        adda.getSource[String].to(Sink(probeB)).run
        Source(elements).to(adda.getSink[String]).run
        verifyWithProbe(elements, probeA)
        verifyWithProbe(elements, probeB)
        adda.awaitCompleted
        successfulTest
      }
      adda.shutdown
    }

    "broadcast from multiple sinks to one source" in {
      val adda = new Adda
      check { (elementsA: List[String], elementsB: List[String]) =>
        val receivedFromAdda = adda.getSource[String].runFold(Set.empty[String]) {
          case (received, next) =>
            received + next
        }
        val sA = Source(elementsA).to(adda.getSink[String])
        val sB = Source(elementsB).to(adda.getSink[String])
        sA.run
        sB.run
        adda.awaitCompleted
        val elementSet = elementsA.toSet ++ elementsB.toSet
        whenReady(receivedFromAdda) { r =>
          r should be(elementSet)
        }
        successfulTest
      }
      adda.shutdown
    }

    "broadcast from multiple sinks to multiple sources" in {
      val adda = new Adda
      try {
        val probe1 = SubscriberProbe[Int]
        val probe2 = SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe1)).run
        adda.getSource[Int].to(Sink(probe2)).run
        val s1 = Source(List(1, 1)).to(adda.getSink[Int])
        val s2 = Source(List(1, 1)).to(adda.getSink[Int])
        // Only execute once both flows are connected.
        s1.run
        s2.run

        probe1.expectSubscription().request(10)
        probe1.expectNext(1)
        probe1.expectNext(1)
        probe1.expectNext(1)
        probe1.expectNext(1)
        probe1.expectComplete

        probe2.expectSubscription().request(10)
        probe2.expectNext(1)
        probe2.expectNext(1)
        probe2.expectNext(1)
        probe2.expectNext(1)
        probe2.expectComplete

        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "support waiting for completion repeatedly" in {
      val adda = new Adda
      try {
        val probe1 = SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe1)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run
        probe1.expectSubscription().request(10)
        probe1.expectNext(1)
        probe1.expectNext(2)
        probe1.expectNext(3)
        probe1.expectComplete
        adda.awaitCompleted
        val probe2 = SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe2)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run
        probe2.expectSubscription().request(10)
        probe2.expectNext(1)
        probe2.expectNext(2)
        probe2.expectNext(3)
        probe2.expectComplete
        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "support canceling a stream before all elements are streamed" in {
      val adda = new Adda
      try {
        val probe = SubscriberProbe[Int]
        adda.getSource[Int].take(1).to(Sink(probe)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run
        probe.expectSubscription().request(10)
        probe.expectNext(1)
        probe.expectComplete
        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "support calling `awaitCompleted' before any sink/source is attached" in {
      val adda = new Adda
      try {
        adda.awaitCompleted
        val probe = SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe)).run
        Source(List(1)).to(adda.getSink[Int]).run
        probe.expectSubscription().request(10)
        probe.expectNext(1)
        probe.expectComplete
        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

  }

}
