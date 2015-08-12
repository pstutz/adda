[![Build Status](https://magnum.travis-ci.com/iHealthTechnologies/adda.svg?token=CJFut42zn19H1aBG2n3Q)](https://magnum.travis-ci.com/iHealthTechnologies/adda)
[![Codacy Badge](https://www.codacy.com/project/badge/aa61de5db91d4cd2bee902ba3534f259)](https://www.codacy.com)

# What problem does Adda solve?
Adda supports publish/subscribe for Akka streams, which allows you to loosely connect different streams.

# How do I use Adda?
# Example Usage
```scala
import akka.stream.scaladsl.{Source, Sink}
import com.ihealthtechnologies.adda.Adda

object CharFlow extends App{
  val adda = new Adda
  implicit val system = adda.system
  implicit val materializer = adda.materializer
  val subscriber = adda.subscribe[Char]
  val elements = List('a','d','d','a')
  Source(elements).to(adda.publish[Char]).run()
  subscriber.map(_.toUpper).to(Sink.foreach(println(_))).run()
}
```

# How are messages ordered?
Subscribers will receive the messages from each publisher in the order in which they were published. Between different publishers there is no guarantee.

# Does Adda support persistence, error recovery, or distributed scalability?
Not yet. Adda builds on Akka and we'd like to explore to what degree Akka features such as persistent actors and its cluster support might help adding these features.

# Types vs. topics: What am I publishing and what am I subscribing to?
// TODO

# Does backpressure work across Adda?
No, not yet, but it would be a nice feature.

# What happens when a subscriber fails to consume the messages fast enough?
The in-memory queue will grow and the application will eventually run out of memory.
// TODO: We should address this case either by supporting backpressure or by disconnecting slow subscribers.

# How does Adda scale?
Each type is handled by its own network of actors, one actor per type and one actor for each subscriber and publisher.

# How does Adda compare to (reactive) Kafka?
Adda runs embedded in the JVM of your application and directly integrates with Akka Streams.
It does not support any of the persistence and distributed scalability features that Kafka supports.
