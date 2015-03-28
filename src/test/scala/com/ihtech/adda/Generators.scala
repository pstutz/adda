package com.ihtech.adda

import scala.collection.immutable.Queue
import org.scalacheck.Gen
import akka.stream.actor.ActorSubscriberMessage.OnNext
import org.scalacheck.Arbitrary

object Generators {

  /**
   * Limit maximum number of tested publishers.
   */
  val maxPublisherCount = 20

  /**
   * Limit maximum number of elements per publisher.
   */
  val maxElementCount = 20

  /**
   * Limit maximum number of tested subscribers.
   */
  val maxSubscriberCount = 20

  /**
   * Limit maximum number of tested subscribers.
   */
  val genSubscriberCount = Gen.choose(0, maxSubscriberCount)

  /**
   * Generates a list of alphanumeric strings that can be published.
   */
  val genStringPublisher = Gen.resize(maxElementCount, Gen.listOf(Gen.alphaStr))

  /**
   * Only test with one or more string publishers. If none are ever added,
   * then the stream is not completed.
   */
  val genListOfStringPublishers = Gen.resize(maxPublisherCount, Gen.nonEmptyListOf(genStringPublisher))

  /**
   * Generates string stream messages.
   */
  val genStringStreamElement = Gen.alphaStr.map(OnNext(_))

  implicit val arbitraryStreamElement = Arbitrary(genStringStreamElement)

  /**
   * Generates a queue of string stream messages.
   */
  val genStringStreamQueue = Gen.nonEmptyListOf(genStringStreamElement).map(Queue(_: _*))

  implicit val arbitraryStreamQueue = Arbitrary(genStringStreamQueue)

}
