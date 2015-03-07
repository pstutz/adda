package com.adda.pubsub

import akka.actor.{ Actor, ActorLogging, ActorRef, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.WatermarkRequestStrategy

class AddaSubscriber(
  private[this] val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(50)

  def receive: Actor.Receive = LoggingReceive {
    case n @ OnNext(next) =>
      broadcastActor ! OnNext(next)
    case OnComplete =>
      context.stop(self)
    case OnError(e) =>
      log.error(e, s"Adda sink received error ${e.getMessage} from $sender")
      e.printStackTrace
  }
}
