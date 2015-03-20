package com.ihtech.adda.integration

import scala.concurrent.ExecutionContext.Implicits.global
import scala.reflect.ClassTag

import org.scalacheck.Arbitrary.arbContainer
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.Checkers

import com.ihtech.adda.Adda
import com.ihtech.adda.Generators.{ genListOfStringPublishers, genStringPublisher, genSubscriberCount }
import com.ihtech.adda.TestConstants.{ probeMinItemsRequested, successfulTest }

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
    adda.subscribe[C].to(Sink(probe)).run
    Source(l).to(adda.publish[C]).run
    verifyWithProbe(l, probe)
    adda.awaitCompleted
    successfulTest
  }

  /**
   * Verifies the probe receives the items in `l' and that the stream completes afterwards.
   */
  def verifyWithProbe[C](l: List[C], probe: SubscriberProbe[C]): Unit = {
    val itemsRequested = math.max(probeMinItemsRequested, l.length)
    probe.expectSubscription().request(itemsRequested)
    for { next <- l } {
      probe.expectNext(next)
    }
    probe.expectComplete
  }

  def setAdditionFold[C](s: Set[C], next: C): Set[C] = s + next

  "Adda" should {

    "support single-publisher/single-subscriber pubsub for strings" in {
      check { (elements: List[String]) =>
        val adda = new Adda
        verifySingleSinkAndSourceFlow(elements, adda)
        adda.shutdown
        successfulTest
      }
    }

    "support single-publisher/single-subscriber pubsub for ints" in {
      check { (elements: List[Int]) =>
        val adda = new Adda
        verifySingleSinkAndSourceFlow(elements, adda)
        adda.shutdown
        successfulTest
      }
    }

    "support single-publisher/single-subscriber pubsub for double" in {
      check { (elements: List[Double]) =>
        val adda = new Adda
        verifySingleSinkAndSourceFlow(elements, adda)
        adda.shutdown
        successfulTest
      }
    }

    "support single-publisher/multiple-subscribers pubsub for strings" in {
      check {
        Prop.forAll(genStringPublisher, genSubscriberCount) {
          (elements: List[String], numberOfSubscribers: Int) =>
            val adda = new Adda
            val probeA = SubscriberProbe[String]
            val probeB = SubscriberProbe[String]
            adda.subscribe[String].to(Sink(probeA)).run
            adda.subscribe[String].to(Sink(probeB)).run
            Source(elements).to(adda.publish[String]).run
            verifyWithProbe(elements, probeA)
            verifyWithProbe(elements, probeB)
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

    "support multiple-publishers/single-subscriber pubsub for strings" in {
      check {
        Prop.forAll(genListOfStringPublishers) {
          (sourceElements: List[List[String]]) =>
            val adda = new Adda
            val receivedFromAdda = adda.subscribe[String].runFold(Set.empty[String])(setAdditionFold)
            val sources = sourceElements.map(Source(_).to(adda.publish[String]))
            sources.foreach(_.run)
            val expectedElementSet = sourceElements.flatten.toSet
            receivedFromAdda.onFailure { case t: Throwable => t.printStackTrace() }
            whenReady(receivedFromAdda)(_ should be(expectedElementSet))
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

    "support multiple-publishers/multiple-subscribers pubsub for strings" in {
      check {
        Prop.forAll(genListOfStringPublishers, genSubscriberCount) {
          (sourceElements: List[List[String]], numberOfSubscribers: Int) =>
            val adda = new Adda
            val subscriberResultSetFutures = List.fill(numberOfSubscribers)(
              adda.subscribe[String].runFold(Set.empty[String])(setAdditionFold))
            val publishers = sourceElements.map(Source(_).to(adda.publish[String]))
            publishers.foreach(_.run)
            val expectedResultSet = sourceElements.flatten.toSet
            subscriberResultSetFutures.foreach { resultSetFuture =>
              whenReady(resultSetFuture)(_ should be(expectedResultSet))
            }
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

    "support waiting for completion repeatedly" in {
      val adda = new Adda
      try {
        val probe1 = SubscriberProbe[Int]
        adda.subscribe[Int].to(Sink(probe1)).run
        Source(List(1, 2, 3)).to(adda.publish[Int]).run
        probe1.expectSubscription().request(10)
        probe1.expectNext(1)
        probe1.expectNext(2)
        probe1.expectNext(3)
        probe1.expectComplete
        adda.awaitCompleted
        val probe2 = SubscriberProbe[Int]
        adda.subscribe[Int].to(Sink(probe2)).run
        Source(List(1, 2, 3)).to(adda.publish[Int]).run
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
        adda.subscribe[Int].take(1).to(Sink(probe)).run
        Source(List(1, 2, 3)).to(adda.publish[Int]).run
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
        adda.subscribe[Int].to(Sink(probe)).run
        Source(List(1)).to(adda.publish[Int]).run
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
