package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue
import scala.concurrent.duration.DurationInt

import org.scalatest.{ BeforeAndAfterAll, Finders, FlatSpec, Matchers }

import com.typesafe.config.ConfigFactory

import akka.actor.{ ActorSystem, Props }
import akka.pattern.ask
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.testkit.{ EventFilter, TestActorRef }
import akka.util.Timeout

class BroadcasterTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = (ActorSystem("TestSystem", ConfigFactory.parseString("""
akka.loggers = ["akka.testkit.TestEventListener"]
""")))

  override def afterAll: Unit = {
    system.shutdown
  }

  private[this] val testStreamElement = OnNext("test")
  implicit val timeout: Timeout = Timeout(300.seconds)

  private[this] val failingHandler: Any => Unit = { a: Any =>
    throw TestException("The handler had a problem.")
  }

  "Broadcaster actor" should "throw exception when the single-element handler fails" in {
    val broadcaster = TestActorRef(Props(new Broadcaster(List(failingHandler))))
    val stringPublisher = broadcaster ? CreatePublisher[String]
    EventFilter[TestException](occurrences = 1) intercept {
      broadcaster ! testStreamElement
      Thread.sleep(200)
    }
  }

  it should "throw exception when the multi-element handler fails" in {
    val broadcaster = TestActorRef(Props(new Broadcaster(List(failingHandler))))
    EventFilter[TestException](occurrences = 1) intercept {
      broadcaster ! Queue[Any](testStreamElement.element, testStreamElement.element)
      Thread.sleep(200)
    }
  }

}
