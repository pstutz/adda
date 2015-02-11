package com.adda.examples

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{Sink, ForeachSink, Flow, Source}
import akka.streams.testkit.StreamTestKit
import com.adda.Adda
import org.scalatest.{FlatSpec, Matchers}
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * Created by jahangirmohammed on 2/11/15.
 */
class CharFlowTest extends FlatSpec with Matchers {

  "Adda" should "take a source of lower case characters and convert them into upper case characters" in {

    val adda = new Adda
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorFlowMaterializer()

    val lowerCaseToUpperFlow: Flow[Char, Char] = Flow[Char].map(f => {
      f.toUpper
    })

    val pub = adda.getSource[Char]

    Source(List('a','d','d','a'))
      .runWith(adda.getSink[Char])

    val probe = StreamTestKit.SubscriberProbe[Char]
    pub.via(lowerCaseToUpperFlow).to(Sink(probe)).run()
    probe.expectSubscription().request(4)
    probe.expectNext('A')
    probe.expectNext('D')
    probe.expectNext('D')
    probe.expectNext('A')


  }
}
