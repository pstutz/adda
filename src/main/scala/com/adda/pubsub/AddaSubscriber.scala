package com.adda.pubsub

import akka.actor.{Actor, ActorLogging, ActorRef, actorRef2Scala}
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnNext}
import akka.stream.actor.WatermarkRequestStrategy

class AddaSubscriber(
  private[this] val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(50)

  def receive: Actor.Receive = LoggingReceive {
    case OnNext(next: AnyRef) =>
      broadcastActor ! ToBroadcast(next)
    case OnComplete =>
      context.stop(self)
  }
}
