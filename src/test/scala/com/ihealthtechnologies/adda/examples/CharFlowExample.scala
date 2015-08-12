/*
 * *
 *  * Licensed to the Apache Software Foundation (ASF) under one or more
 *  * contributor license agreements.  See the NOTICE file distributed with
 *  * this work for additional information regarding copyright ownership.
 *  * The ASF licenses this file to You under the Apache License, Version 2.0
 *  * (the "License"); you may not use this file except in compliance with
 *  * the License.  You may obtain a copy of the License at
 *  *
 *  *    http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package com.ihealthtechnologies.adda.examples

import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.prop.Checkers

import com.ihealthtechnologies.adda.Adda
import com.ihealthtechnologies.adda.TestHelpers.verifyWithProbe

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber.manualProbe


/**
 * This simple example shows how one can use Adda to publish a list of
 * characters to two subscribers.
 */
class CharFlowExample extends FlatSpec with Matchers with Checkers {

  "Adda" should "stream some charactes from a source to two sinks" in {

    check { (chars: List[Char]) =>
      val adda = new Adda
      implicit val system = adda.system
      implicit val materializer = adda.materializer
      val probes = List(manualProbe[Char], manualProbe[Char])
      probes.foreach(probe => adda.subscribe[Char].to(Sink(probe)).run())
      Source(chars).to(adda.publish[Char]).run()
      probes.foreach(verifyWithProbe(chars, _))
      adda.awaitCompleted()
      adda.shutdown()
      true // Check requires for the return value to be a boolean.
    }
  }

}
