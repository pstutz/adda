package com.adda

import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.{ ActorFlowMaterializer, ActorFlowMaterializerSettings }
import akka.streams.testkit.{ StreamTestKit, AkkaSpec }

class AddaTest extends AkkaSpec {
  val settings = ActorFlowMaterializerSettings(system)
    .withInputBuffer(initialSize = 2, maxSize = 16)

  implicit val materializer = ActorFlowMaterializer(settings)

  "Adda" must {
    "broadcast from one sink to one source" in {
      val adda = new Adda
      try {
        val probe = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run

        probe.expectSubscription().request(10)
        probe.expectNext(1)
        probe.expectNext(2)
        probe.expectNext(3)
        probe.expectComplete

        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "broadcast from one sink to multiple sources" in {
      val adda = new Adda
      try {
        val probe1 = StreamTestKit.SubscriberProbe[Int]
        val probe2 = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe1)).run
        adda.getSource[Int].to(Sink(probe2)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run

        probe1.expectSubscription().request(10)
        probe1.expectNext(1)
        probe1.expectNext(2)
        probe1.expectNext(3)
        probe1.expectComplete

        probe2.expectSubscription().request(10)
        probe2.expectNext(1)
        probe2.expectNext(2)
        probe2.expectNext(3)
        probe2.expectComplete

        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "broadcast from multiple sinks to one source" in {
      val adda = new Adda
      try {
        val probe = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe)).run
        Source(List(1, 1)).to(adda.getSink[Int]).run
        Source(List(1)).to(adda.getSink[Int]).run

        probe.expectSubscription().request(10)
        probe.expectNext(1)
        probe.expectNext(1)
        probe.expectNext(1)
        probe.expectComplete

        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "broadcast from multiple sinks to multiple sources" in {
      val adda = new Adda
      try {
        val probe1 = StreamTestKit.SubscriberProbe[Int]
        val probe2 = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe1)).run
        adda.getSource[Int].to(Sink(probe2)).run
        Source(List(1)).to(adda.getSink[Int]).run
        Source(List(1)).to(adda.getSink[Int]).run

        probe1.expectSubscription().request(10)
        probe1.expectNext(1)
        probe1.expectNext(1)
        probe1.expectComplete

        probe2.expectSubscription().request(10)
        probe2.expectNext(1)
        probe2.expectNext(1)
        probe2.expectComplete

        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

  }

}
