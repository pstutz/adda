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

package com.ihealthtechnologies.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import org.scalatest.prop.Checkers

import com.ihealthtechnologies.adda.TestConstants.successfulTest
import com.ihealthtechnologies.adda.TestHelpers.{ testSystem, verifyWithProbe }

import akka.actor.{ Props, actorRef2Scala }
import akka.stream.ActorMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe

class SubscriberTest extends FlatSpec with Checkers with Matchers with BeforeAndAfterAll {

  implicit val system = testSystem(enableTestEventListener = true)
  implicit val materializer = ActorMaterializer()

  override def afterAll: Unit = {
    system.shutdown
  }

  "Subscriber actor" should "stream a received empty string" in {
    val streamProbe = manualProbe[String]
    val subscriber = system.actorOf(Props(new Subscriber[String]))
    val source = Source(ActorPublisher[String](subscriber))
    source.to(Sink(streamProbe)).run
    subscriber ! OnNext("")
    subscriber ! Complete
    verifyWithProbe[String](List(""), streamProbe)
  }

  it should "stream received elements" in {
    check { (streamStrings: List[String]) =>
      val streamProbe = manualProbe[String]
      val subscriber = system.actorOf(Props(new Subscriber[String]))
      val source = Source(ActorPublisher[String](subscriber))
      source.to(Sink(streamProbe)).run
      for { streamElement <- streamStrings } {
        subscriber ! OnNext(streamElement)
      }
      subscriber ! Complete
      verifyWithProbe[String](streamStrings, streamProbe)
      successfulTest
    }
  }

  it should "stream received bulk elements" in {
    check { (streamStringLists: List[List[String]]) =>
      val streamProbe = manualProbe[String]
      val subscriber = system.actorOf(Props(new Subscriber[String]))
      val source = Source(ActorPublisher[String](subscriber))
      source.to(Sink(streamProbe)).run
      for { streamStringList <- streamStringLists } {
        subscriber ! Queue(streamStringList: _*)
      }
      subscriber ! Complete
      verifyWithProbe(streamStringLists.flatten, streamProbe)
      successfulTest
    }
  }

}
