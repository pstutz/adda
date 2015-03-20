package com.ihtech.adda

import org.scalacheck.Gen

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

}
