package com.ihtech.adda.examples

import akka.stream.{ ActorFlowMaterializer, ActorFlowMaterializerSettings }
import akka.stream.scaladsl.{ Flow, FlowGraph, Sink, Source }
import akka.stream.scaladsl.FlowGraph.Implicits.SourceArrow
import akka.stream.testkit.AkkaSpec
import akka.stream.testkit.StreamTestKit.SubscriberProbe

class FlowGraphSpec extends AkkaSpec {

  val settings = ActorFlowMaterializerSettings(system)
    .withInputBuffer(initialSize = 2, maxSize = 16)

  implicit val materializer = ActorFlowMaterializer(settings)
  "FlowGraphs" must {

    "successfully run a simple flow" in {
      val p = Source(List(1, 2, 3)).runWith(Sink.publisher[Int])
      val subProbe = SubscriberProbe[Int]
      val flow = Flow[Int].map(_ * 2)
      FlowGraph.closed() { implicit builder ⇒
        import FlowGraph.Implicits._
        Source(p) ~> flow ~> Sink(subProbe)
      }.run()
      val sub = subProbe.expectSubscription()
      sub.request(10)
      subProbe.expectNext(1 * 2)
      subProbe.expectNext(2 * 2)
      subProbe.expectNext(3 * 2)
      subProbe.expectComplete()
    }
  }
}
