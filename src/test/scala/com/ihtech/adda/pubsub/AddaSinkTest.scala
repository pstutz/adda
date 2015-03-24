package com.ihtech.adda.pubsub

import org.scalatest.{ BeforeAndAfterAll, Finders, FlatSpec, Matchers }

import com.typesafe.config.ConfigFactory

import akka.actor.{ ActorSystem, PoisonPill, Props, actorRef2Scala }
import akka.stream.actor.ActorSubscriberMessage.OnError
import akka.testkit.{ EventFilter, TestActorRef, TestProbe }

case class TestException(msg: String) extends Exception(msg)

class AddaSinkTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = (ActorSystem("TestSystem", ConfigFactory.parseString("""
akka.loggers = ["akka.testkit.TestEventListener"]
""")))

  override def afterAll {
    system.shutdown
  }

  "AddaSinkActor" should "log received errors" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = system.actorOf(Props(new AddaSink(trackCompletion, broadcasterProbe.ref)))
    EventFilter[TestException](occurrences = 1) intercept {
      subscriber ! OnError(TestException("Just testing."))
    }
    subscriber ! PoisonPill
  }

  it should "throw an exception when it receives CanSendNext whilst not in queuing mode" in {
    val broadcasterProbe = TestProbe()
    val trackCompletion = false
    val subscriber = TestActorRef(new AddaSink(trackCompletion, broadcasterProbe.ref))
    intercept[IllegalActorState] {
      subscriber.receive(CanSendNext)
    }
    subscriber ! PoisonPill
  }

}
