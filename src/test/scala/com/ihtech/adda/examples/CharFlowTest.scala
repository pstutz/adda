package com.ihtech.adda.examples

import org.scalatest.{ FlatSpec, Matchers }

import com.ihtech.adda.Adda

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Flow, Sink, Source }
import akka.stream.testkit.StreamTestKit

class CharFlowTest extends FlatSpec with Matchers {

  "Adda" should "take a source of lower case characters and convert them into upper case characters" in {

    val adda = new Adda
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorFlowMaterializer()

    val lowerCaseToUpperFlow: Flow[Char, Char, Unit] = Flow[Char].map(f => {
      f.toUpper
    })

    val probe = StreamTestKit.SubscriberProbe[Char]

    adda.subscribe[Char].via(lowerCaseToUpperFlow).to(Sink(probe)).run()
    Source(List('a', 'd', 'd', 'a'))
      .runWith(adda.publish[Char])

    probe.expectSubscription().request(4)
    probe.expectNext('A')
    probe.expectNext('D')
    probe.expectNext('D')
    probe.expectNext('A')
    probe.expectComplete()

    adda.awaitCompleted()
    adda.shutdown()

  }
}
