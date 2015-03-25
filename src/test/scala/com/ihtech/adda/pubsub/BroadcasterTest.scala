package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, Finders, FlatSpec, Matchers }

import com.typesafe.config.ConfigFactory

import akka.actor.{ ActorSystem, Props }
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.testkit.{ EventFilter, TestActorRef }

class BroadcasterTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = (ActorSystem("TestSystem", ConfigFactory.parseString("""
akka.loggers = ["akka.testkit.TestEventListener"]
""")))

  override def afterAll: Unit = {
    system.shutdown
  }

  private[this] val testStreamElement = OnNext("test")

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

}
