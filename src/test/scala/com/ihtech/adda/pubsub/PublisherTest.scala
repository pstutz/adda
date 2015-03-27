package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }

import com.ihtech.adda.TestHelpers.testSystem

import akka.actor.{ PoisonPill, Props, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.testkit.{ EventFilter, TestActorRef, TestProbe }

case class TestException(msg: String) extends Exception(msg)

class PublisherTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = testSystem(enableTestEventListener = true)

  override def afterAll: Unit = {
    system.shutdown
  }

  private[this] val testString = "test"
  private[this] val testStreamElement = OnNext(testString)
  private[this] val testQueue = Queue[String](testString, testString)

  "Publisher actor" should "forward received stream elements to the broadcaster" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    publisher ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
  }

  it should "log received errors in default mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    EventFilter[TestException](occurrences = 1) intercept {
      publisher ! OnError(TestException("Just testing."))
    }
    publisher ! PoisonPill
  }

  it should "log received errors in queueing mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    publisher ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
    EventFilter[TestException](occurrences = 1) intercept {
      publisher ! OnError(TestException("Just testing."))
    }
  }

  it should "throw an exception when it receives CanSendNext whilst not in queuing mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    EventFilter[IllegalActorState](occurrences = 1) intercept {
      broadcasterProbe.send(publisher, CanPublishNext)
    }
  }

  it should "report completion to broadcaster when tracking is enabled" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = true
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
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
    broadcasterProbe.expectMsg(testQueue)
  }

}
