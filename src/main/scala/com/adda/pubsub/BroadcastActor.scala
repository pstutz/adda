package com.adda.pubsub

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.{ ClassTag, _ }
import com.adda.interfaces.GraphSerializable
import com.adda.interfaces.TripleStore
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.ActorRef
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.event.LoggingReceive
import akka.util.Timeout
import akka.actor.Terminated

final case object AwaitCompleted

final case object Completed

abstract class Tagged[C: ClassTag] {
  val className = implicitly[ClassTag[C]].runtimeClass.getName
}

final case class CreatePublisher[C: ClassTag]() extends Tagged[C] {
  def taggedSourceActor = new SourceActor[C]()
}

final case class CreateSubscriber[C: ClassTag]() extends Tagged[C]

/**
 * Once the number of non-completed sinks for a class was > 0, and then falls back to 0, all
 * sources connected with that class are completed.
 *
 * The `awaitingIdle' list keeps track of actors that are waiting for all processing to complete.
 */
class BroadcastActor(private[this] val store: TripleStore) extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(20 seconds)

  private[this] val pubsub = new PubSubManager[ActorRef]
  private[this] var awaitingIdle: List[ActorRef] = Nil

  def receive = LoggingReceive {
    case c @ CreatePublisher() =>
      val source = context.actorOf(Props(c.taggedSourceActor))
      context.watch(source)
      val topic =
        pubsub.addSubscriber(topic = c.className, source)
      sender ! source
    case c @ CreateSubscriber() =>
      val sink = context.actorOf(Props(new SinkActor(self)))
      context.watch(sink)
      pubsub.addPublisher(c.className, sink)
      sender ! sink
    case a @ AddaEntity(e) =>
      e match {
        // If the entity is graph serializable, add it to the store.
        case g: GraphSerializable =>
          val triples = g.asGraph
          triples.foreach(store.addTriple(_))
        case other => // Do nothing.
      }
      val publisher = sender
      val topic = pubsub.topicForPublisher(publisher)
      val subscribersForTopic = pubsub.subscribersForTopic(topic)
      subscribersForTopic.foreach(_ ! a)
    case Terminated(actor) =>
      if (pubsub.isPublisher(actor)) {
        val topic = pubsub.topicForPublisher(actor)
        pubsub.removePublisher(actor)
        if (pubsub.publishersForTopic(topic).isEmpty) {
          completeSubscribers(topic)
        }
      } else { // isSubscriber
        pubsub.removeSubscriber(actor)
      }
      if (pubsub.isCompleted) notifyCompleted()
    case AwaitCompleted =>
      awaitingIdle = sender :: awaitingIdle
      if (pubsub.isCompleted) notifyCompleted()
  }

  private[this] def completeSubscribers(topic: String) = {
    pubsub.subscribersForTopic(topic).foreach(_ ! Complete)
  }

  private[this] def notifyCompleted() {
    awaitingIdle.foreach(_ ! Completed)
    awaitingIdle = Nil
  }

}
