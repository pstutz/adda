/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */


package com.ihealthtechnologies.adda

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
