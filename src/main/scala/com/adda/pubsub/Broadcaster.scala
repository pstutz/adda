package com.adda.pubsub

import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag

import com.adda.interfaces.{ GraphSerializable, TripleStore }

import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Terminated, actorRef2Scala }
import akka.event.LoggingReceive
import akka.util.Timeout

final case object AwaitCompleted

final case object Completed

abstract class Tagged[C: ClassTag] {
  val className = implicitly[ClassTag[C]].runtimeClass.getName
}

final case class CreatePublisher[C: ClassTag]() extends Tagged[C] {
  def createPublisher: AddaPublisher[C] = new AddaPublisher[C]()
}

final case class CreateSubscriber[C: ClassTag](isTemporary: Boolean) extends Tagged[C] {
  def createSubscriber(broadcaster: ActorRef): AddaSubscriber[C] = new AddaSubscriber[C](broadcaster)
}

/**
 * Once the number of non-completed sinks for a class was > 0, and then falls back to 0, all
 * sources connected with that class are completed.
 *
 * The `awaitingIdle' list keeps track of actors that are waiting for all processing to complete.
 */
class Broadcaster(private[this] val store: TripleStore) extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(20 seconds)

  private[this] val pubSub = new PubSubManager

  def receive: Actor.Receive = LoggingReceive {
    case c @ CreatePublisher() =>
      val publisher = createPublisher(c)
      sender ! publisher
    case c @ CreateSubscriber(_) =>
      val subscriber = createSubscriber(c)
      sender ! subscriber
    case toBroadcast @ ToBroadcast(e) =>
      serializeToGraph(e)
      pubSub.broadcastToPublishers(fromSubscriber = sender, itemToBroadcast = toBroadcast)
    case Terminated(actor) =>
      pubSub.remove(actor)
    case AwaitCompleted =>
      pubSub.awaitingCompleted(sender)
  }

  private[this] def createPublisher[C](c: CreatePublisher[C]): ActorRef = {
    val publisher = context.actorOf(Props(c.createPublisher))
    context.watch(publisher)
    pubSub.addPublisher(topic = c.className, publisher)
    publisher
  }

  private[this] def createSubscriber[C](c: CreateSubscriber[C]): ActorRef = {
    val subscriber = context.actorOf(Props(c.createSubscriber(self)))
    c.isTemporary match {
      case false => // We watch and keep track of the non-temporary subscribers. 
        context.watch(subscriber)
        pubSub.addSubscriber(topic = c.className, subscriber)
      case true => // We do not track/watch temporary subscribers.
    }
    subscriber
  }

  private[this] def serializeToGraph(e: Any): Unit = {
    e match {
      // If the entity is graph serializable, add it to the store.
      case g: GraphSerializable =>
        val triples = g.asGraph
        triples.foreach(store.addTriple(_))
      case _ => // Do nothing.
    }
  }

}
