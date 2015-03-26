package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, Finders, FlatSpec, Matchers }

import com.typesafe.config.ConfigFactory

import akka.actor.{ ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.testkit.{ EventFilter, TestActorRef, TestProbe }

case class TestException(msg: String) extends Exception(msg)

class PublisherTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = (ActorSystem("TestSystem", ConfigFactory.parseString("""
akka.loggers = ["akka.testkit.TestEventListener"]
""")))

  override def afterAll: Unit = {
    system.shutdown
  }

  private[this] val testStreamElement = OnNext("test")

  "Publisher actor" should "forward received stream elements to the broadcaster" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    subscriber ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
  }

  it should "log received errors in default mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    EventFilter[TestException](occurrences = 1) intercept {
      subscriber ! OnError(TestException("Just testing."))
    }
    subscriber ! PoisonPill
  }

  it should "log received errors in queueing mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    subscriber ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
    EventFilter[TestException](occurrences = 1) intercept {
      subscriber ! OnError(TestException("Just testing."))
    }
  }

  it should "throw an exception when it receives CanSendNext whilst not in queuing mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = TestActorRef(new Publisher(trackCompletion, broadcasterProbe.ref))
    intercept[IllegalActorState] {
      publisher.receive(CanPublishNext)
    }
  }

  it should "report completion to broadcaster when tracking is enabled" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = true
    val publisher = TestActorRef(new Publisher(trackCompletion, broadcasterProbe.ref))
    publisher ! OnComplete
    broadcasterProbe.expectMsg(Completed)
  }

  it should "not report completion to the broadcaster when tracking is disabled" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = TestActorRef(new Publisher(trackCompletion, broadcasterProbe.ref))
    publisher ! OnComplete
    broadcasterProbe.expectNoMsg()
  }

  it should "queue elements and bulk send them to the broadcaster" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    publisher ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
    publisher ! testStreamElement
    publisher ! testStreamElement
    publisher ! CanPublishNext
    broadcasterProbe.expectMsg(Queue[Any](testStreamElement.element, testStreamElement.element))
  }

}
