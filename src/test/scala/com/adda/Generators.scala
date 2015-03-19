package com.adda

import org.scalacheck.Gen

object Generators {

  val (minSubscriberCount, maxSubscriberCount) = (0, 10000)

  /**
   * Bias subscriber count frequency towards small numbers.
   */
  val subscriberCountFrequencies = for {
    (numberOfSubscribers, index) <- (minSubscriberCount to maxSubscriberCount).zipWithIndex
    frequency = (numberOfSubscribers * maxSubscriberCount*maxSubscriberCount) / (index*index + 1)
  } yield frequency -> Gen.const(numberOfSubscribers)

  /**
   * Limit maximum number of tested sinks to 10000.
   */
  val genSubscriberCount = Gen.frequency(subscriberCountFrequencies: _*)

  /**
   * Generates a list of alphanumeric strings that can be published.
   */
  val genPublishableStrings = Gen.listOf(Gen.alphaStr)

  /**
   * Only test with one or more string publishers. If none are ever added,
   * then the stream is not completed.
   */
  val genListOfPublishableStrings = Gen.nonEmptyListOf(genPublishableStrings)

}
