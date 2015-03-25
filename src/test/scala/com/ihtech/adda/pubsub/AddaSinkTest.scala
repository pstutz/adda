package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, Finders, FlatSpec, Matchers }

import com.typesafe.config.ConfigFactory

import akka.actor.{ ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.testkit.{ EventFilter, TestActorRef, TestProbe }

case class TestException(msg: String) extends Exception(msg)

class AddaSinkTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = (ActorSystem("TestSystem", ConfigFactory.parseString("""
akka.loggers = ["akka.testkit.TestEventListener"]
""")))

  override def afterAll: Unit = {
    system.shutdown
  }

  private[this] val testStreamElement = OnNext("test")

  "AddaSink actor" should "forward received stream elements to the broadcaster" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(Props(new AddaSink(trackCompletion, broadcasterProbe.ref)))
    subscriber ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
  }

  it should "log received errors in default mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(Props(new AddaSink(trackCompletion, broadcasterProbe.ref)))
    EventFilter[TestException](occurrences = 1) intercept {
      subscriber ! OnError(TestException("Just testing."))
    }
    subscriber ! PoisonPill
  }

  it should "log received errors in queueing mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(Props(new AddaSink(trackCompletion, broadcasterProbe.ref)))
    subscriber ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
    EventFilter[TestException](occurrences = 1) intercept {
      subscriber ! OnError(TestException("Just testing."))
    }
  }

  it should "throw an exception when it receives CanSendNext whilst not in queuing mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(new AddaSink(trackCompletion, broadcasterProbe.ref))
    intercept[IllegalActorState] {
      subscriber.receive(CanSendNext)
    }
  }

  it should "report completion to broadcaster when tracking is enabled" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = true
    val subscriber = TestActorRef(new AddaSink(trackCompletion, broadcasterProbe.ref))
    subscriber ! OnComplete
    broadcasterProbe.expectMsg(Completed)
  }

  it should "not report completion to the broadcaster when tracking is disabled" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(new AddaSink(trackCompletion, broadcasterProbe.ref))
    subscriber ! OnComplete
    broadcasterProbe.expectNoMsg()
  }

  it should "queue elements and bulk send them to the broadcaster" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = system.actorOf(Props(new AddaSink(trackCompletion, broadcasterProbe.ref)))
    subscriber ! testStreamElement
    broadcasterProbe.expectMsg(testStreamElement)
    subscriber ! testStreamElement
    subscriber ! testStreamElement
    subscriber ! CanSendNext
    broadcasterProbe.expectMsg(Queue[Any](testStreamElement.element, testStreamElement.element))
  }

}
