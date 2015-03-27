package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue
import scala.reflect.ClassTag

import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }

import com.ihtech.adda.TestHelpers.testSystem

import akka.actor.{ ActorRef, ActorRefFactory, Props }
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.testkit.{ EventFilter, TestActorRef, TestProbe }

class PublisherInjector(injectedActorRef: ActorRef, trackCompletion: Boolean)
  extends CreatePublisher(trackCompletion: Boolean) {
  override def createPublisher(
    f: ActorRefFactory, uniqueId: Long, broadcaster: ActorRef): ActorRef = injectedActorRef
}

class SubscriberInjector[C: ClassTag](injectedActorRef: ActorRef) extends CreateSubscriber[C] {
  override def createSubscriber(
    f: ActorRefFactory, uniqueId: Long): ActorRef = injectedActorRef
}

class BroadcasterTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = testSystem(enableTestEventListener = true)

  override def afterAll: Unit = {
    system.shutdown
  }

  private[this] val testString = "test"
  private[this] val testStreamElement = OnNext(testString)
  private[this] val testQueue = Queue[String](testString, testString)

  private[this] val failingHandler: Any => Unit = { a: Any =>
    throw TestException("The handler had a problem.")
  }

  "Broadcaster actor" should "throw exception when the single-element handler fails" in {
    val broadcaster = TestActorRef(Props(new Broadcaster(List(failingHandler))))
    EventFilter[TestException](occurrences = 1) intercept {
      broadcaster ! testStreamElement
    }
  }

  it should "throw exception when the multi-element handler fails" in {
    val broadcaster = TestActorRef(Props(new Broadcaster(List(failingHandler))))
    EventFilter[TestException](occurrences = 1) intercept {
      broadcaster ! Queue[Any](testStreamElement.element, testStreamElement.element)
    }
  }

  it should "broadcast a message from a publisher to all subscribers" in {
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
    publisher.send(broadcaster, testStreamElement)
    subscriberA.expectMsg(testStreamElement)
    subscriberB.expectMsg(testStreamElement)
    publisher.expectMsg(CanPublishNext)
  }

  it should "broadcast a bulk message from a publisher to all subscribers" in {
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
    publisher.send(broadcaster, testQueue)
    subscriberA.expectMsg(testQueue)
    subscriberB.expectMsg(testQueue)
    publisher.expectMsg(CanPublishNext)
  }

}
