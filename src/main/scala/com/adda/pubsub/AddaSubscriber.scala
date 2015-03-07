package com.adda.pubsub

import scala.collection.mutable

import akka.actor.{ Actor, ActorLogging, ActorRef, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.WatermarkRequestStrategy

class AddaSubscriber(
  val isTemporary: Boolean,
  val broadcaster: ActorRef) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(50)
  val queue = mutable.Queue.empty[OnNext]
  var canSendNext = true
  var shouldComplete = false

  def receive: Actor.Receive = LoggingReceive {
    case n: OnNext =>
      if (canSendNext) {
        broadcaster ! n
        canSendNext = false
      } else {
        queue += n
      }
    case CanSendNext =>
      if (queue.isEmpty) {
        canSendNext = true
        if (shouldComplete) complete
      } else {
        val n = queue.dequeue
        broadcaster ! n
      }
    case OnComplete =>
      shouldComplete = true
      if (queue.isEmpty && canSendNext) complete
    case OnError(e) =>
      log.error(e, s"Adda sink received error ${e.getMessage} from $sender")
      e.printStackTrace
  }

  def complete(): Unit = {
    if (!isTemporary) broadcaster ! Completed
    context.stop(self)
  }

}
