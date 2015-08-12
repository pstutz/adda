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


package com.ihealthtechnologies.adda.integration

import org.scalatest.{ Finders, FlatSpec, Matchers }

import com.ihealthtechnologies.adda.Adda

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe

class BulkPublishingTest extends FlatSpec with Matchers {

  "Adda" should "deliver bulked messages in the correct order" in {
    val adda = new Adda
    implicit val system = adda.system
    implicit val materializer = adda.materializer

    val maxElements = 1000000
    val ascendingInts = 1 to maxElements

    val probe = manualProbe[Int]
    adda.subscribe[Int].to(Sink(probe)).run
    Source(ascendingInts).to(adda.publish[Int]).run

    probe.expectSubscription().request(maxElements)
    for { i <- ascendingInts } {
      probe.expectNext(i)
    }
    probe.expectComplete()
    adda.awaitCompleted()
    adda.shutdown()
  }

}
