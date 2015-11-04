/**
 * Copyright (C) ${project.inceptionYear} Mycila (mathieu.carbou@gmail.com)
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

import java.util.concurrent.atomic.AtomicInteger

import scala.util.Random

import org.scalatest.{ FlatSpec, Matchers }

import com.ihealthtechnologies.adda.{ Adda, DelayingHandler }

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe

class HandlerTest extends FlatSpec with Matchers {

  "A privileged handler" should "receive all published entities" in {

    val counter = new AtomicInteger

    def intHandler(e: Any) = {
      e match {
        case i: Int => counter.addAndGet(i)
        case _      => throw new Exception(s"Handler received an unexpected message.")
      }
    }

    val adda = new Adda(List(intHandler))
    implicit val system = adda.system
    implicit val materializer = adda.materializer

    val elements = 1000000

    val probe = manualProbe[Int]
    val in = Source(1 to elements).to(adda.publish[Int])
    val out = adda.subscribe[Int].to(Sink(probe))
    out.run
    in.run

    probe.expectSubscription().request(elements)
    for { i <- 1 to elements } {
      probe.expectNext(i)
    }
    probe.expectComplete()

    val entrySum = (1 to elements).sum
    counter.get should be(entrySum)

    adda.awaitCompleted()
    adda.shutdown()
  }

}
