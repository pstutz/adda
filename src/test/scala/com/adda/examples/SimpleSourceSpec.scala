package com.adda.examples

import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorFlowMaterializer, ActorFlowMaterializerSettings}
import akka.streams.testkit.{StreamTestKit, AkkaSpec}

class SimpleSourceSpec extends AkkaSpec {
  val settings = ActorFlowMaterializerSettings(system)
    .withInputBuffer(initialSize = 2, maxSize = 16)

  implicit val materializer = ActorFlowMaterializer(settings)

  "A source" must {
    "steam its contents " in {
      val probe = StreamTestKit.SubscriberProbe[Int]
      Source(List(1,2,3)).to(Sink(probe)).run()
      probe.expectSubscription().request(10)
      probe.expectNext(1)
      probe.expectNext(2)
      probe.expectNext(3)
      probe.expectComplete()
    }
  }
}