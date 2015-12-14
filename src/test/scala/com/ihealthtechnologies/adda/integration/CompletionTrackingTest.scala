/**
 * Copyright (C) 2015 Cotiviti Labs (nexgen.admin@cotiviti.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.ihealthtechnologies.adda.integration

import org.scalatest.{ Finders, FlatSpec, Matchers }
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe
import com.ihealthtechnologies.adda.Adda

class CompletionTrackingTest extends FlatSpec with Matchers {

  "Completion tracking" should "be inactive when it is disabled" in {
    val adda = new Adda()
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorMaterializer()
    val elements = 100
    val probe = manualProbe[Int]
    adda.subscribe[Int].to(Sink(probe)).run
    Source(1 to elements).to(adda.publish[Int](trackCompletion = false)).run
    probe.expectSubscription().request(elements)
    for { i <- 1 to elements } {
      probe.expectNext(i)
    }
    probe.expectNoMsg()
    adda.shutdown()
  }

}
