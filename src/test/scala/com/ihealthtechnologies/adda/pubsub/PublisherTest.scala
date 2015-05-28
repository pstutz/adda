package com.ihealthtechnologies.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, FlatSpec, Matchers }
import org.scalatest.prop.Checkers

import com.ihealthtechnologies.adda.Generators.arbitraryStreamElement
import com.ihealthtechnologies.adda.TestConstants.successfulTest
import com.ihealthtechnologies.adda.TestHelpers.testSystem

import akka.actor.{ Props, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.testkit.{ EventFilter, TestProbe }

case class TestException(msg: String) extends Exception(msg)

class PublisherTest extends FlatSpec with Checkers with Matchers with BeforeAndAfterAll {

  implicit val system = testSystem(enableTestEventListener = true)

  override def afterAll: Unit = {
    system.shutdown
  }

  "Publisher actor" should "forward a received string and complete the stream" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = true
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    val emptyStringMsg = OnNext("")
    publisher ! emptyStringMsg
    broadcasterProbe.expectMsg(emptyStringMsg)
    publisher ! OnComplete
    publisher ! CanPublishNext
    broadcasterProbe.expectMsg(Completed)
  }

  it should "forward received stream elements to the broadcaster" in {
    check { (streamElement: OnNext) =>
      val broadcasterProbe = TestProbe()
      val trackCompletion = false
      val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
      publisher ! streamElement
      broadcasterProbe.expectMsg(streamElement)
      successfulTest
    }
  }

  it should "log received errors when no elements are queued" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val publisher = system.actorOf(Props(new Publisher(trackCompletion, broadcasterProbe.ref)))
    EventFilter[TestException](occurrences = 1) intercept {
      publisher ! OnError(TestException("Just testing."))
    }
  }

  it should "log received errors when elements are queued" in {
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
