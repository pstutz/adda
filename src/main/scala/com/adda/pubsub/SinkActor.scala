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

class SinkActor(
  private[this] val broadcastActor: ActorRef,
  sinkClassName: String) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(50)

  override def preStart(): Unit = {
    broadcastActor ! RegisterSink(sinkClassName)
  }

  def receive = {
    case OnNext(next: AnyRef) =>
      broadcastActor ! AddaEntity(next)
    case OnComplete =>
      broadcastActor ! RemoveSink(sinkClassName)
      context.stop(self)
  }
}
