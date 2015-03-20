package com.ihtech.adda.examples

import akka.stream.{ ActorFlowMaterializer, ActorFlowMaterializerSettings }
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.{ AkkaSpec, StreamTestKit }

class SimpleSourceTest extends AkkaSpec {
  val settings = ActorFlowMaterializerSettings(system)
    .withInputBuffer(initialSize = 2, maxSize = 16)

  implicit val materializer = ActorFlowMaterializer(settings)

  "A source" must {
    "stream its contents " in {
      val probe = StreamTestKit.SubscriberProbe[Int]
      Source(List(1, 2, 3)).to(Sink(probe)).run()
      probe.expectSubscription().request(10)
      probe.expectNext(1)
      probe.expectNext(2)
      probe.expectNext(3)
      probe.expectComplete()
    }
  }
}
