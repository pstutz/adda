package com.adda.entities

import akka.actor.{ActorLogging, Actor}
import akka.stream.actor.ActorSubscriberMessage.{OnComplete, OnError, OnNext}
import akka.stream.actor.{WatermarkRequestStrategy, ActorSubscriber, ActorPublisher}
import java.math.BigInteger
import akka.actor._
import akka.stream.actor._
import ActorPublisherMessage._

/**
 * Created by jmohammed on 09/02/15.
 */

case class ClaimLine(line: Map[String, String])

class ClaimLinePublisher extends ActorPublisher[ClaimLine] with ActorLogging {

  def receive = {
    case Request(cnt) =>
      log.debug("[ClaimLinePublisher] Received Request ({}) from Subscriber", cnt)
      sendClaimLines()
    case Cancel =>
      log.info("[ClaimLinePublisher] Cancel Message Received -- Stopping")
      context.stop(self)
    case _ =>
  }

  def sendClaimLines() {
    while (isActive && totalDemand > 0) {
      onNext(nextClaimLine())
    }
  }

  def nextClaimLine(): ClaimLine = {
    ClaimLine(Map("cptCode" -> "A2015", "dos" -> "20140201"))
  }
}


class Pricer(delay: Long) extends ActorSubscriber with ActorLogging {
  val requestStrategy = WatermarkRequestStrategy(1)

  def receive = {
    case OnNext(claimline: ClaimLine) =>
      log.debug("[Pricer] Received  Claim line: {}", claimline)
      Thread.sleep(delay)
    case OnError(err: Exception) =>
      log.error(err, "[Pricer] Receieved Exception in Pricer")
      context.stop(self)
    case OnComplete =>
      log.info("[Pricer] Claim Line Stream Completed!")
      context.stop(self)
    case _ =>
  }
}