package com.ihtech.adda.examples

import org.scalatest.{ FlatSpec, Matchers }
import org.scalatest.prop.Checkers

import com.ihtech.adda.Adda
import com.ihtech.adda.TestHelpers.verifyWithProbe

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.StreamTestKit.SubscriberProbe

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
      val probes = List(SubscriberProbe[Char], SubscriberProbe[Char])
      probes.foreach(probe => adda.subscribe[Char].to(Sink(probe)).run())
      Source(chars).to(adda.publish[Char]).run()
      probes.foreach(verifyWithProbe(chars, _))
      adda.awaitCompleted()
      adda.shutdown()
      true // Check requires for the return value to be a boolean.
    }
  }

}
