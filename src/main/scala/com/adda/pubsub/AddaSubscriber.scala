package com.adda.pubsub

import scala.collection.mutable
import akka.actor.{ Actor, ActorLogging, ActorRef, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.WatermarkRequestStrategy
import scala.collection.immutable.Queue

class AddaSubscriber(
  val isTemporary: Boolean,
  val broadcaster: ActorRef) extends ActorSubscriber with ActorLogging {

  val requestStrategy = WatermarkRequestStrategy(50)
  var bulkMessage = Queue.empty[Any]
  var canSendNext = true
  var shouldComplete = false

  def receive: Actor.Receive = LoggingReceive {
    case n @ OnNext(e) =>
      if (canSendNext) {
        broadcaster ! n
        canSendNext = false
      } else {
        bulkMessage = bulkMessage.enqueue(e)
      }
    case CanSendNext =>
      if (bulkMessage.isEmpty) {
        canSendNext = true
        if (shouldComplete) complete
      } else {
        broadcaster ! bulkMessage
        bulkMessage = Queue.empty[Any]
      }
    case OnComplete =>
      shouldComplete = true
      if (bulkMessage.isEmpty && canSendNext) complete
    case OnError(e) =>
      log.error(e, s"Adda sink received error ${e.getMessage} from $sender")
      e.printStackTrace
  }

  def complete(): Unit = {
    if (!isTemporary) broadcaster ! Completed
    context.stop(self)
  }

}
