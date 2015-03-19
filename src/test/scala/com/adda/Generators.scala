package com.adda

import org.scalacheck.Gen
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
  val genSubscriberCount = Gen.choose(0, 20)

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
