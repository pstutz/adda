package com.ihtech.adda

import scala.collection.immutable.Queue

import akka.stream.actor.ActorSubscriberMessage.OnNext

object TestConstants {

  /**
   * A stream probe has to request at least 1 item.
   */
  val probeMinItemsRequested = 1

  /**
   * Placeholder to use in ScalaTest property tests that do not produce a boolean return value.
   */
  val successfulTest = true

  val testString = "test"
  val testStreamElement = OnNext(testString)
  val testQueue = Queue[String](testString, testString)

}
