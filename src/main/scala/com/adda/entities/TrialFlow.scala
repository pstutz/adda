package com.adda.entities


import akka.actor.{Props, ActorSystem}
import akka.stream.actor.{ActorPublisher, ActorSubscriber}
import akka.stream.scaladsl.{Sink, Source}

/**
 * Created by jmohammed on 09/02/15.
 */
//object TrialFlow  extends App{
//
//  implicit val system = ActorSystem("test-system")
//  val subscriber = system.actorOf(Props[SampleSubscriber],"sample-subscriber")
//
//  implicit val mat = FlowMaterializer()
//  val foreachSink = Sink.foreach[Int](println)
//
//  Source(1 to 100).sub
//
////  Source(1 to 100).filter(_%2 == 0).runWith(foreachSink)(mat)
//
//
//  Source(1 to 100).filter(_%2 == 0).runWith(subscriber)
//
//}


object TrialFlow extends App {
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



