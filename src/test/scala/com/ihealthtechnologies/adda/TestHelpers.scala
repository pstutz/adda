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


package com.ihealthtechnologies.adda

import scala.annotation.tailrec
import scala.reflect.ClassTag

import com.ihealthtechnologies.adda.TestConstants.{ probeMinItemsRequested, successfulTest }
import com.typesafe.config.ConfigFactory

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.TestSubscriber
import akka.stream.testkit.TestSubscriber.manualProbe

object TestHelpers {

  private[this] def insertStringIf(insert: String, condition: Boolean) = if (condition) insert else ""

  /**
   * Test system that is not too chatty and allows to enable the test event listener.
   */
  def testSystem(enableTestEventListener: Boolean = false): ActorSystem = {
    ActorSystem("TestSystem", ConfigFactory.parseString(
      s"""
      |akka {
      |  loggers = [${insertStringIf("akka.testkit.TestEventListener", enableTestEventListener)}]
      |  loglevel = "WARNING"
      |  stdout-loglevel = "WARNING"
      |  log-dead-letters = off
      |  log-dead-letters-during-shutdown = off
      |  actor.debug.receive = off
      |  akka.actor.debug.lifecycle = off
      |}
    """.stripMargin))
  }

  /**
   * Returns true iff the subsequence appears in the sequence.
   * Used in tests to check if per-publisher ordering is violated.
   */
  @tailrec
  def containsSubsequence(sequence: List[_], subsequence: List[_]): Boolean = {
    (sequence, subsequence) match {
      case (_, Nil) =>
        true
      case (Nil, remainder) =>
        false
      case (seqHead :: seqTail, subseqHead :: subseqTail) =>
        if (seqHead == subseqHead) {
          containsSubsequence(seqTail, subseqTail)
        } else {
          containsSubsequence(seqTail, subsequence)
        }
    }
  }

  /**
   * Verifies that a sink receives the elements in `l', when they are streamed into Adda by a source.
   */
  def verifySingleSinkAndSourceFlow[C: ClassTag](l: List[C], adda: Adda)(implicit system: ActorSystem): Boolean = {
    implicit val materializer = ActorMaterializer()
    val probe = manualProbe[C]
    adda.subscribe[C].to(Sink(probe)).run
    Source(l).to(adda.publish[C]).run
    verifyWithProbe(l, probe)
    adda.awaitCompleted
    successfulTest
  }

  /**
   * Verifies that the probe receives the items in `l' and that the stream completes afterwards.
   */
  def verifyWithProbe[C](l: List[C], probe: TestSubscriber.ManualProbe[C]): Unit = {
    val itemsRequested = math.max(probeMinItemsRequested, l.length)
    probe.expectSubscription().request(itemsRequested)
    probe.expectNextN(l)
    probe.expectComplete
  }

  def aggregateIntoSet[C](s: Set[C], next: C): Set[C] = s + next

  def aggregateIntoList[C](s: List[C], next: C): List[C] = next :: s

}
