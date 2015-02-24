package com.adda.pubsub

import akka.actor.{ Actor, ActorLogging, ActorRef, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnNext }
import akka.stream.actor.WatermarkRequestStrategy
import scala.reflect.ClassTag

class AddaSubscriber[C: ClassTag](
  private[this] val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(50)

  def receive: Actor.Receive = LoggingReceive {
    case n @ OnNext(next) =>
      next match {
        case successfulMatch: C => broadcastActor ! ToBroadcast(successfulMatch)
      }
    case OnComplete =>
      context.stop(self)
  }
}
