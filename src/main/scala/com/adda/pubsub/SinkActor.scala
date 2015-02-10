package com.adda.pubsub

import scala.language.postfixOps

import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.actorRef2Scala
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.OnComplete
import akka.stream.actor.ActorSubscriberMessage.OnError
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.stream.actor.WatermarkRequestStrategy

class SinkActor(private[this] val broadcastActor: ActorRef) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(50)

  def receive = {
    case OnNext(next: AnyRef) =>
      log.debug(s"[SinkActor] received new message $next.")
      broadcastActor ! AddaEntity(next)
    case OnComplete =>
      context.stop(self)
  }
}
