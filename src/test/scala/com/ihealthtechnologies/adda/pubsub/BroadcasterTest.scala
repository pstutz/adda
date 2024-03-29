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
import scala.concurrent.Await
import scala.reflect.ClassTag

import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import org.scalatest.prop.Checkers

import com.ihealthtechnologies.adda.Generators.{ arbitraryStreamElement, arbitraryStreamQueue }
import com.ihealthtechnologies.adda.TestConstants.successfulTest
import com.ihealthtechnologies.adda.TestHelpers.testSystem

import akka.actor.{ ActorRef, ActorRefFactory, Props, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.testkit.{ EventFilter, TestProbe }
import scala.concurrent.duration._

class PublisherInjector(injectedActorRef: ActorRef, trackCompletion: Boolean)
  extends CreatePublisher(trackCompletion: Boolean) {
  override def createPublisher(
    f: ActorRefFactory, uniqueId: Long, broadcaster: ActorRef): ActorRef = injectedActorRef
}

class SubscriberInjector[C: ClassTag](injectedActorRef: ActorRef) extends CreateSubscriber[C] {
  override def createSubscriber(
    f: ActorRefFactory, uniqueId: Long): ActorRef = injectedActorRef
}

class BroadcasterTest extends FlatSpec with Checkers with Matchers with BeforeAndAfterAll {

  implicit val system = testSystem(enableTestEventListener = true)

  override def afterAll: Unit = {
    Await.ready(system.terminate(), 300.seconds)
  }

  private[this] val failingHandler: Any => Unit = { a: Any =>
    throw TestException("The handler had a problem.")
  }

  "Broadcaster actor" should "throw one exception when a handler fails on a single element" in {
    check { (streamElement: OnNext) =>
      val broadcaster = system.actorOf(Props(new Broadcaster(List(failingHandler))))
      EventFilter[TestException](occurrences = 1) intercept {
        broadcaster ! streamElement
      }
      successfulTest
    }
  }

  it should "throw one exception when a handler fails on elements of a queue" in {
    check { (streamQueue: Queue[OnNext]) =>
      val broadcaster = system.actorOf(Props(new Broadcaster(List(failingHandler))))
      EventFilter[TestException](occurrences = 1) intercept {
        broadcaster ! streamQueue
      }
      successfulTest
    }
  }

  it should "broadcast a message from a publisher to all subscribers" in {
    check { (streamElement: OnNext) =>
      val broadcaster = system.actorOf(Props(new Broadcaster(Nil)))
      val adda = TestProbe()
      val publisher = TestProbe()
      val subscriberA = TestProbe()
      val subscriberB = TestProbe()
      adda.send(broadcaster, new PublisherInjector(publisher.ref, trackCompletion = true))
      adda.expectMsg(publisher.ref)
      adda.send(broadcaster, new SubscriberInjector[String](subscriberA.ref))
      adda.expectMsg(subscriberA.ref)
      adda.send(broadcaster, new SubscriberInjector[String](subscriberB.ref))
      adda.expectMsg(subscriberB.ref)
      publisher.send(broadcaster, streamElement)
      subscriberA.expectMsg(streamElement)
      subscriberB.expectMsg(streamElement)
      publisher.expectMsg(CanPublishNext)
      successfulTest
    }
  }

  it should "broadcast a bulk message from a publisher to all subscribers" in {
    check { (streamQueue: Queue[OnNext]) =>
      val broadcaster = system.actorOf(Props(new Broadcaster(Nil)))
      val adda = TestProbe()
      val publisher = TestProbe()
      val subscriberA = TestProbe()
      val subscriberB = TestProbe()
      adda.send(broadcaster, new PublisherInjector(publisher.ref, trackCompletion = true))
      adda.expectMsg(publisher.ref)
      adda.send(broadcaster, new SubscriberInjector[String](subscriberA.ref))
      adda.expectMsg(subscriberA.ref)
      adda.send(broadcaster, new SubscriberInjector[String](subscriberB.ref))
      adda.expectMsg(subscriberB.ref)
      publisher.send(broadcaster, streamQueue)
      subscriberA.expectMsg(streamQueue)
      subscriberB.expectMsg(streamQueue)
      publisher.expectMsg(CanPublishNext)
      successfulTest
    }
  }

}
