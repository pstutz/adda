package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import org.scalatest.prop.Checkers

import com.ihtech.adda.Generators.arbitraryStreamElement
import com.ihtech.adda.TestConstants.successfulTest
import com.ihtech.adda.TestHelpers.testSystem

import akka.actor.{ Props, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.testkit.{ EventFilter, TestProbe }

case class TestException(msg: String) extends Exception(msg)

class PublisherTest extends FlatSpec with Checkers with Matchers with BeforeAndAfterAll {

  implicit val system = testSystem(enableTestEventListener = true)

  override def afterAll: Unit = {
    system.shutdown
  }

  "Publisher actor" should "forward received stream elements to the broadcaster" in {
    check { (streamElement: OnNext) =>
      val broadcasterProbe = TestProbe()
      val trackCompletion = false
      val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
      publisher ! streamElement
      broadcasterProbe.expectMsg(streamElement)
      successfulTest
    }
  }

  it should "log received errors in default mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    EventFilter[TestException](occurrences = 1) intercept {
      publisher ! OnError(TestException("Just testing."))
    }
  }

  it should "log received errors in queueing mode" in {
    check { (streamElement: OnNext) =>
      val broadcasterProbe = TestProbe()
      val trackCompletion = false
      val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
      publisher ! streamElement
      broadcasterProbe.expectMsg(streamElement)
      EventFilter[TestException](occurrences = 1) intercept {
        publisher ! OnError(TestException("Just testing."))
      }
      successfulTest
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

  it should "report completion to the broadcaster when tracking is enabled" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = true
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    publisher ! OnComplete
    broadcasterProbe.expectMsg(Completed)
  }

  it should "not report completion to the broadcaster when tracking is disabled" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    publisher ! OnComplete
    broadcasterProbe.expectNoMsg()
  }

  it should "queue elements and bulk send them to the broadcaster" in {
    check { (streamElements: List[OnNext]) =>
      val broadcasterProbe = TestProbe()
      val trackCompletion = false
      val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
      streamElements match {
        case Nil =>
        case firstElement :: remainingElements =>
          publisher ! firstElement
          broadcasterProbe.expectMsg(firstElement)
          for { element <- remainingElements } {
            publisher ! element
          }
          publisher ! CanPublishNext
          remainingElements match {
            case Nil               => broadcasterProbe.expectNoMsg()
            case oneElement :: Nil => broadcasterProbe.expectMsg(oneElement)
            case elements: Any     => broadcasterProbe.expectMsg(Queue(remainingElements.map(_.element): _*))
          }
      }
      successfulTest
    }
  }

}
