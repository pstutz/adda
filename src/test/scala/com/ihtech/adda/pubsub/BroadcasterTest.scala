package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue
import scala.reflect.ClassTag

import org.scalatest.{BeforeAndAfterAll, FlatSpec, Matchers}
import org.scalatest.prop.Checkers

import com.ihtech.adda.Generators.{arbitraryStreamElement, arbitraryStreamQueue}
import com.ihtech.adda.TestConstants.successfulTest
import com.ihtech.adda.TestHelpers.testSystem

import akka.actor.{ActorRef, ActorRefFactory, Props}
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.testkit.{EventFilter, TestActorRef, TestProbe}

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
    system.shutdown
  }

  private[this] val failingHandler: Any => Unit = { a: Any =>
    throw TestException("The handler had a problem.")
  }

  "Broadcaster actor" should "throw one exception when a handler fails on a single element" in {
    check { (streamElement: OnNext) =>
      val broadcaster = TestActorRef(Props(new Broadcaster(List(failingHandler))))
      EventFilter[TestException](occurrences = 1) intercept {
        broadcaster ! streamElement
      }
      successfulTest
    }
  }

  it should "throw one exception when a handler fails on elements of a queue" in {
    check { (streamQueue: Queue[OnNext]) =>
      val broadcaster = TestActorRef(Props(new Broadcaster(List(failingHandler))))
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
