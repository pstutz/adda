package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue

import org.scalatest.{ BeforeAndAfterAll, Finders, FlatSpec, Matchers }

import com.ihtech.adda.TestHelpers.{ testSystem, verifyWithProbe }

import akka.actor.{ Props, actorRef2Scala }
import akka.stream.ActorFlowMaterializer
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.StreamTestKit.SubscriberProbe

class SubscriberTest extends FlatSpec with Matchers with BeforeAndAfterAll {

  implicit val system = testSystem(enableTestEventListener = true)
  implicit val materializer = ActorFlowMaterializer()

  override def afterAll: Unit = {
    system.shutdown
  }

  private[this] val testString = "test"
  private[this] val testStreamElement = OnNext(testString)
  private[this] val testQueue = Queue[String](testString, testString)

  "Subscriber actor" should "stream received elements" in {
    val streamProbe = SubscriberProbe[String]
    val subscriber = system.actorOf(Props(new Subscriber[String]))
    val source = Source(ActorPublisher[String](subscriber))
    source.to(Sink(streamProbe)).run
    subscriber ! testStreamElement
    subscriber ! testStreamElement
    subscriber ! Complete
    verifyWithProbe(List(testString, testString), streamProbe)
  }

  it should "stream received bulk elements" in {
    val streamProbe = SubscriberProbe[String]
    val subscriber = system.actorOf(Props(new Subscriber[String]))
    val source = Source(ActorPublisher[String](subscriber))
    source.to(Sink(streamProbe)).run
    subscriber ! testQueue
    subscriber ! testQueue
    subscriber ! Complete
    verifyWithProbe(List(testString, testString, testString, testString), streamProbe)
  }

}
