package com.ihtech.adda.integration

import scala.concurrent.ExecutionContext.Implicits.global

import org.scalacheck.Arbitrary.arbContainer
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.Checkers

import com.ihtech.adda.Adda
import com.ihtech.adda.Generators.{ genListOfStringPublishers, genStringPublisher, genSubscriberCount }
import com.ihtech.adda.TestConstants.successfulTest
import com.ihtech.adda.TestHelpers.{ aggregateIntoList, aggregateIntoSet, containsSubsequence, verifySingleSinkAndSourceFlow, verifyWithProbe }

import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.AkkaSpec
import akka.stream.testkit.StreamTestKit.SubscriberProbe

class SinkAndSourceTest extends AkkaSpec with Checkers with ScalaFutures {
  implicit val materializer = ActorFlowMaterializer()

  private[this] val subsequenceNotFound =
    "Sequence published by one of the publishers was not a subsequence of the sequence received by the subscriber."

  "Adda" should {

    "support single-publisher/single-subscriber pubsub for strings" in {
      check { (strings: List[String]) =>
        val adda = new Adda
        verifySingleSinkAndSourceFlow(strings, adda)
        adda.shutdown
        successfulTest
      }
    }

    "support single-publisher/single-subscriber pubsub for ints" in {
      check { (strings: List[Int]) =>
        val adda = new Adda
        verifySingleSinkAndSourceFlow(strings, adda)
        adda.shutdown
        successfulTest
      }
    }

    "support single-publisher/single-subscriber pubsub for doubles" in {
      check { (strings: List[Double]) =>
        val adda = new Adda
        verifySingleSinkAndSourceFlow(strings, adda)
        adda.shutdown
        successfulTest
      }
    }

    "support single-publisher/multiple-subscribers pubsub for strings" in {
      check {
        Prop.forAll(genStringPublisher, genSubscriberCount) {
          (strings: List[String], numberOfSubscribers: Int) =>
            val adda = new Adda
            val probeA = SubscriberProbe[String]
            val probeB = SubscriberProbe[String]
            adda.subscribe[String].to(Sink(probeA)).run
            adda.subscribe[String].to(Sink(probeB)).run
            Source(strings).to(adda.publish[String]).run
            verifyWithProbe(strings, probeA)
            verifyWithProbe(strings, probeB)
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

    "support multiple-publishers/single-subscriber pubsub for strings" in {
      check {
        Prop.forAll(genListOfStringPublishers) {
          (listOfStringLists: List[List[String]]) =>
            val adda = new Adda
            // Ordered list of received elements.
            val receivedFromAdda = adda.subscribe[String]
              .runFold(List.empty[String])(aggregateIntoList)
              .map(_.reverse)
            val sources = listOfStringLists.map(Source(_).to(adda.publish[String]))
            sources.foreach(_.run)
            val expectedElementSet = listOfStringLists.flatten.toSet
            whenReady(receivedFromAdda) { orderedListOfReceived =>
              orderedListOfReceived.toSet should be(expectedElementSet)
              listOfStringLists.foreach { inputSequence =>
                assert(containsSubsequence(orderedListOfReceived, inputSequence), subsequenceNotFound)
              }
            }
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

    "support multiple-publishers/multiple-subscribers pubsub for strings" in {
      check {
        Prop.forAll(genListOfStringPublishers, genSubscriberCount) {
          (listOfStringLists: List[List[String]], numberOfSubscribers: Int) =>
            val adda = new Adda
            val subscriberOrderedReceivedFutures = List.fill(numberOfSubscribers)(
              adda.subscribe[String]
                .runFold(List.empty[String])(aggregateIntoList)
                .map(_.reverse))
            val publishers = listOfStringLists.map(Source(_).to(adda.publish[String]))
            publishers.foreach(_.run)
            val expectedElementSet = listOfStringLists.flatten.toSet
            subscriberOrderedReceivedFutures.foreach { orderedResultFuture =>
              whenReady(orderedResultFuture) { orderedListOfReceived =>
                orderedListOfReceived.toSet should be(expectedElementSet)
                listOfStringLists.foreach { inputSequence =>
                  assert(containsSubsequence(orderedListOfReceived, inputSequence), subsequenceNotFound)
                }
              }
            }
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

    "support waiting for completion repeatedly" in {
      check {
        Prop.forAll(genListOfStringPublishers, genSubscriberCount) {
          (listOfStringLists: List[List[String]], numberOfSubscribers: Int) =>
            val adda = new Adda
            for { strings <- listOfStringLists } {
              val subscriberResultSetFutures = List.fill(numberOfSubscribers)(
                adda.subscribe[String].runFold(Set.empty[String])(aggregateIntoSet))
              Source(strings).to(adda.publish[String]).run
              val expectedResultSet = strings.toSet
              subscriberResultSetFutures.foreach { resultSetFuture =>
                whenReady(resultSetFuture)(_ should be(expectedResultSet))
              }
              adda.awaitCompleted
            }
            adda.shutdown
            successfulTest
        }
      }
    }

    "support canceling a stream before all elements are streamed" in {
      check {
        Prop.forAll(genStringPublisher, genSubscriberCount) {
          (strings: List[String], numberOfSubscribers: Int) =>
            val adda = new Adda
            val probe = SubscriberProbe[String]
            adda.subscribe[String].take(1).to(Sink(probe)).run
            Source(strings).to(adda.publish[String]).run
            verifyWithProbe(strings.take(1), probe)
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

    "support calling `awaitCompleted' before any sink/source is attached" in {
      check {
        Prop.forAll(genStringPublisher, genSubscriberCount) {
          (strings: List[String], numberOfSubscribers: Int) =>
            val adda = new Adda
            adda.awaitCompleted
            val probe = SubscriberProbe[String]
            adda.subscribe[String].to(Sink(probe)).run
            Source(strings).to(adda.publish[String]).run
            verifyWithProbe(strings, probe)
            adda.awaitCompleted
            adda.shutdown
            successfulTest
        }
      }
    }

  }

}
