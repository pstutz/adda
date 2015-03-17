package com.adda.integration

import com.adda.Adda

import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ Sink, Source }
import akka.stream.testkit.{ AkkaSpec, StreamTestKit }

class SinkAndSourceTest extends AkkaSpec {
  implicit val materializer = ActorFlowMaterializer()

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
        val s1 = Source(List(1, 1)).to(adda.getSink[Int])
        val s2 = Source(List(1, 1)).to(adda.getSink[Int])
        // Only execute once both flows are connected.
        s1.run
        s2.run

        probe.expectSubscription().request(10)
        probe.expectNext(1)
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
        val s1 = Source(List(1, 1)).to(adda.getSink[Int])
        val s2 = Source(List(1, 1)).to(adda.getSink[Int])
        // Only execute once both flows are connected.
        s1.run
        s2.run

        probe1.expectSubscription().request(10)
        probe1.expectNext(1)
        probe1.expectNext(1)
        probe1.expectNext(1)
        probe1.expectNext(1)
        probe1.expectComplete

        probe2.expectSubscription().request(10)
        probe2.expectNext(1)
        probe2.expectNext(1)
        probe2.expectNext(1)
        probe2.expectNext(1)
        probe2.expectComplete

        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "support waiting for completion repeatedly" in {
      val adda = new Adda
      try {
        val probe1 = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe1)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run
        probe1.expectSubscription().request(10)
        probe1.expectNext(1)
        probe1.expectNext(2)
        probe1.expectNext(3)
        probe1.expectComplete
        adda.awaitCompleted
        val probe2 = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe2)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run
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

    "support canceling a stream before all elements are streamed" in {
      val adda = new Adda
      try {
        val probe = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].take(1).to(Sink(probe)).run
        Source(List(1, 2, 3)).to(adda.getSink[Int]).run
        probe.expectSubscription().request(10)
        probe.expectNext(1)
        probe.expectComplete
        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

    "support calling `awaitCompleted' before any sink/source is attached" in {
      val adda = new Adda
      try {
        adda.awaitCompleted
        val probe = StreamTestKit.SubscriberProbe[Int]
        adda.getSource[Int].to(Sink(probe)).run
        Source(List(1)).to(adda.getSink[Int]).run
        probe.expectSubscription().request(10)
        probe.expectNext(1)
        probe.expectComplete
        adda.awaitCompleted
      } finally {
        adda.shutdown
      }
    }

  }

}
