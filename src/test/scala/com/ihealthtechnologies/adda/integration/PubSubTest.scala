/**
 * Copyright (C) 2015 Cotiviti Labs (nexgen.admin@cotiviti.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ihealthtechnologies.adda.integration

import org.scalatest.time.{Millis, Seconds, Span}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global

import org.scalacheck.Arbitrary.arbContainer
import org.scalacheck.Prop
import org.scalacheck.Prop.propBoolean
import org.scalatest.{BeforeAndAfterAll, Finders, FlatSpec}
import org.scalatest.Matchers.{be, convertToAnyShouldWrapper}
import org.scalatest.concurrent.ScalaFutures
import org.scalatest.prop.Checkers

import com.ihealthtechnologies.adda.Adda
import com.ihealthtechnologies.adda.Generators.{genListOfStringPublishers, genStringPublisher, genSubscriberCount}
import com.ihealthtechnologies.adda.TestConstants.successfulTest
import com.ihealthtechnologies.adda.TestHelpers._

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.testkit.TestSubscriber.manualProbe
import scala.concurrent.duration._

class PubSubTest extends FlatSpec with Checkers with ScalaFutures with BeforeAndAfterAll {
  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorMaterializer()
  private[this] val span = 10
  private[this] val interval = 15
  implicit override val patienceConfig = PatienceConfig(timeout = Span(span, Seconds), interval = Span(interval, Millis))

  private[this] val subsequenceNotFound =
    "Sequence published by one of the publishers was not a subsequence of the sequence received by the subscriber."

  override def afterAll(): Unit = {
    Await.ready(system.terminate(), 300.seconds)
  }

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

  it should "support single-publisher/single-subscriber when the max publisher queue size is 1" in {
    check { (strings: List[String]) =>
      val adda = new Adda(maxPublisherQueueSize = 1)
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
      adda.subscribe[Int].to(Sink.fromSubscriber(intProbe)).run
      adda.subscribe[Double].to(Sink.fromSubscriber(doubleProbe)).run
      adda.subscribe[String].to(Sink.fromSubscriber(stringProbe)).run
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
          adda.subscribe[String].to(Sink.fromSubscriber(probeA)).run
          adda.subscribe[String].to(Sink.fromSubscriber(probeB)).run
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
          for {strings <- listOfStringLists} {
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
          adda.subscribe[String].take(1).to(Sink.fromSubscriber(probe)).run
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
          adda.subscribe[String].to(Sink.fromSubscriber(probe)).run
          Source(strings).to(adda.publish[String]).run
          verifyWithProbe(strings, probe)
          adda.awaitCompleted
          adda.shutdown
          successfulTest
      }
    }
  }

}
