package com.ihtech.adda

import scala.annotation.tailrec
import scala.reflect.ClassTag

import com.ihtech.adda.TestConstants.{ probeMinItemsRequested, successfulTest }

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.StreamTestKit.SubscriberProbe

object TestHelpers {

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
    implicit val materializer = ActorFlowMaterializer()
    val probe = SubscriberProbe[C]
    adda.subscribe[C].to(Sink(probe)).run
    Source(l).to(adda.publish[C]).run
    verifyWithProbe(l, probe)
    adda.awaitCompleted
    successfulTest
  }

  /**
   * Verifies that the probe receives the items in `l' and that the stream completes afterwards.
   */
  def verifyWithProbe[C](l: List[C], probe: SubscriberProbe[C]): Unit = {
    val itemsRequested = math.max(probeMinItemsRequested, l.length)
    probe.expectSubscription().request(itemsRequested)
    for { next <- l } {
      probe.expectNext(next)
    }
    probe.expectComplete
  }

  def aggregateIntoSet[C](s: Set[C], next: C): Set[C] = s + next

  def aggregateIntoList[C](s: List[C], next: C): List[C] = next :: s

}
