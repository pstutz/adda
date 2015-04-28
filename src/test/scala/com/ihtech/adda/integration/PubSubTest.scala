package com.ihtech.adda.integration

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalacheck.Arbitrary.arbContainer
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import org.scalatest.{ Finders, FlatSpec }
import org.scalatest.Matchers.{ be, convertToAnyShouldWrapper }
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.Checkers

import com.ihtech.adda.Adda
import com.ihtech.adda.Generators.{ genListOfStringPublishers, genStringPublisher, genSubscriberCount }
import com.ihtech.adda.TestConstants.successfulTest
import com.ihtech.adda.TestHelpers._

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe

class PubSubTest extends FlatSpec with Checkers with ScalaFutures {
  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorFlowMaterializer()

  private[this] val subsequenceNotFound =
    "Sequence published by one of the publishers was not a subsequence of the sequence received by the subscriber."

  "PubSub" should "support streaming an empty list" in {
    val adda = new Adda
    verifySingleSinkAndSourceFlow[String](List.empty[String], adda)
    adda.shutdown
  }

  it should "support streaming an empty string" in {
    val adda = new Adda
    verifySingleSinkAndSourceFlow[String](List("a"), adda)
    adda.shutdown
  }

  it should "support single-publisher/single-subscriber scenarios for strings" in {
    check { (strings: List[String]) =>
      val adda = new Adda
      verifySingleSinkAndSourceFlow(strings, adda)
      adda.shutdown
      successfulTest
    }
  }

  it should "support single-publisher/single-subscriber scenarios for multiple types at the same time" in {
    check { (ints: List[Int], doubles: List[Double], strings: List[String]) =>
      val adda = new Adda
      val intProbe = manualProbe[Int]
      val doubleProbe = manualProbe[Double]
      val stringProbe = manualProbe[String]
      adda.subscribe[Int].to(Sink(intProbe)).run
      adda.subscribe[Double].to(Sink(doubleProbe)).run
      adda.subscribe[String].to(Sink(stringProbe)).run
      val publishers = List(
        Source(ints).to(adda.publish[Int]),
        Source(doubles).to(adda.publish[Double]),
        Source(strings).to(adda.publish[String]))
      publishers.foreach(_.run)
      verifyWithProbe(ints, intProbe)
      verifyWithProbe(doubles, doubleProbe)
      verifyWithProbe(strings, stringProbe)
      adda.awaitCompleted
      adda.shutdown
      successfulTest
    }
  }

  it should "support single-publisher/multiple-subscribers scenarios for strings" in {
    check {
      Prop.forAll(genStringPublisher, genSubscriberCount) {
        (strings: List[String], numberOfSubscribers: Int) =>
          val adda = new Adda
          val probeA = manualProbe[String]
          val probeB = manualProbe[String]
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

  it should "support multiple-publishers/single-subscriber scenarios for strings" in {
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

  it should "support multiple-publisher scenarios when the publishers are not tracked" in {
    check {
      Prop.forAll(genListOfStringPublishers) {
        (listOfStringLists: List[List[String]]) =>
          val adda = new Adda
          val sources = listOfStringLists.map(Source(_).to(adda.publish[String](trackCompletion = false)))
          sources.foreach(_.run)
          adda.shutdown
          successfulTest
      }
    }
  }

  it should "support multiple-publishers/multiple-subscribers scenarios for strings" in {
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

  it should "support waiting for completion repeatedly" in {
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

  it should "support canceling a stream before all elements are streamed" in {
    check {
      Prop.forAll(genStringPublisher, genSubscriberCount) {
        (strings: List[String], numberOfSubscribers: Int) =>
          val adda = new Adda
          val probe = manualProbe[String]
          adda.subscribe[String].take(1).to(Sink(probe)).run
          Source(strings).to(adda.publish[String]).run
          verifyWithProbe(strings.take(1), probe)
          adda.awaitCompleted
          adda.shutdown
          successfulTest
      }
    }
  }

  it should "support calling `awaitCompleted' before any publisher/subscriber is created" in {
    check {
      Prop.forAll(genStringPublisher, genSubscriberCount) {
        (strings: List[String], numberOfSubscribers: Int) =>
          val adda = new Adda
          adda.awaitCompleted
          val probe = manualProbe[String]
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
