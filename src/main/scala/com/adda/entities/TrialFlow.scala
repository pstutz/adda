package com.adda.entities


import akka.actor.{Props, ActorSystem}
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl.{SubscriberSink, Sink, Source}
import akka.stream.ActorFlowMaterializer

/**
 * Created by jmohammed on 09/02/15.
 */
object TrialFlow  extends App{

  implicit val system = ActorSystem("test-system")
  val publisherActor = system.actorOf(Props[ClaimLinePublisher])
  val publisher = ActorPublisher[ClaimLine](publisherActor)

  implicit val mat = ActorFlowMaterializer()

  val subscriberActor = system.actorOf(Props(new Pricer(5)))
  val subscriber = ActorSubscriber[ClaimLine](subscriberActor)

  Source(publisher).runWith(SubscriberSink(subscriber))(mat)

}


object TrialRunDirectPubSub extends App {
  val system = ActorSystem("example-stream-system")

  startSimplePubSubExample(system)

  def startSimplePubSubExample(system: ActorSystem) {
    system.log.info("Starting Publisher")
    val publisherActor = system.actorOf(Props[ClaimLinePublisher])
    val publisher = ActorPublisher[ClaimLine](publisherActor)

    system.log.info("Starting Subscriber")
    val subscriberActor = system.actorOf(Props(new Pricer(5)))
    val subscriber = ActorSubscriber[ClaimLine](subscriberActor)

    system.log.info("Subscribing to Publisher")
    publisher.subscribe(subscriber)
  }
}



