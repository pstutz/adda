package com.adda.entities

import akka.stream.actor.WatermarkRequestStrategy
import akka.actor.{Actor, ActorLogging, ActorRef}
import akka.stream.actor.ActorSubscriber
import akka.stream.actor.ActorPublisherMessage.Request
import akka.stream.actor.ActorSubscriberMessage.{ OnComplete, OnError, OnNext }
import akka.stream.actor.ActorPublisher
import akka.stream.actor.ActorPublisherMessage.Cancel
import java.util.concurrent.LinkedBlockingQueue

case class AddaEntity(entity: AnyRef)

case class AddFeedPublisher(publisher: ActorRef)


class GeneralSource extends ActorPublisher[AnyRef] with ActorLogging {

  val queue = new LinkedBlockingQueue[AnyRef]

  def receive = {
    case AddaEntity(e) =>
      queue.put(e)
    case Request(cnt) =>
      log.debug("[GeneralSource] Received Request ({}) from Subscriber", cnt)
      sendClaimLines()
    case Cancel =>
      log.info("[GeneralSource] Cancel Message Received -- Stopping")
      context.stop(self)
    case _ =>
  }

  def sendClaimLines() {
    while (isActive && totalDemand > 0) {
      onNext(queue.take)
    }
  }

}

class GeneralSink extends ActorSubscriber with ActorLogging {
  val requestStrategy = WatermarkRequestStrategy(50)

  var publishers = List.empty[ActorRef]

  def receive = {
    case AddFeedPublisher(publisher) =>
      publishers = publisher :: publishers
    case OnNext(something: AnyRef) =>
      log.debug(s"[GeneralSink] Received $something.")
      publishers.foreach { _ ! AddaEntity(something) }
    case OnError(err: Exception) =>
      log.error(err, s"[GeneralSink] Received Exception $err.")
      context.stop(self)
    case OnComplete =>
      log.info(s"[GeneralSink] Stream Completed!")
      context.stop(self)
    case _ =>
  }
}
