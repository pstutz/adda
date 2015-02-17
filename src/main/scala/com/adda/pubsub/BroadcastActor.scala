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
 * Broadcast actor maintains four maps:
 * The input map keeps track of all sinks that receive instances of a given class.
 * The output map keeps track of all sources that publish instances of a given class.
 * The source to class and sink to class maps keep track of which sink/source actors
 * are handling which classes.
 *
 * Once the number of non-completed sinks for a class was > 0, and then falls back to 0, all
 * sources connected with that class are completed.
 *
 * The `awaitingIdle' list keeps track of actors that are waiting for all processing to complete.
 */
class BroadcastActor(private[this] val store: TripleStore) extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(20 seconds)

  private[this] var activeSinks = Map.empty[String, Set[ActorRef]].withDefaultValue(Set.empty)
  private[this] var activeSources = Map.empty[String, Set[ActorRef]].withDefaultValue(Set.empty)
  private[this] var classForSource = Map.empty[ActorRef, String]
  private[this] var classForSink = Map.empty[ActorRef, String]
  private[this] var awaitingIdle: List[ActorRef] = Nil

  def receive = LoggingReceive {
    case c @ CreatePublisher() =>
      val source = context.actorOf(Props(c.taggedSourceActor))
      context.watch(source)
      val sourceClassName = c.className
      val updatedSourcesForClass = activeSources(sourceClassName) + source
      activeSources += (c.className -> updatedSourcesForClass)
      classForSource += (source -> sourceClassName)
      sender ! source
    case c @ CreateSubscriber() =>
      val sink = context.actorOf(Props(new SinkActor(self)))
      context.watch(sink)
      val sinkClassName = c.className
      val updatedSinksForClass = activeSinks(sinkClassName) + sink
      activeSinks += (c.className -> updatedSinksForClass)
      classForSink += (sink -> sinkClassName)
      sender ! sink
    case a @ AddaEntity(e) =>
      e match {
        // If the entity is graph serializable, add it to the store.
        case g: GraphSerializable =>
          val triples = g.asGraph
          triples.foreach(store.addTriple(_))
        case other => // Do nothing.
      }
      val sink = sender
      val entityClass = classForSink(sink)
      val sourcesForClass = activeSources(entityClass)
      sourcesForClass.foreach(_ ! a)
    case Terminated(sourceOrSink) =>
      val isSource = classForSource.contains(sourceOrSink)
      if (isSource) {
        val source = sourceOrSink
        val sourceClassName = classForSource(source)
        val updatedSourcesForClass = activeSources(sourceClassName) - source
        if (updatedSourcesForClass.isEmpty) {
          activeSources -= sourceClassName
        } else {
          activeSources += (sourceClassName -> updatedSourcesForClass)
        }
        classForSource -= source
      } else {
        val sink = sourceOrSink
        val sinkClassName = classForSink(sink)
        val updatedSinksForClass = activeSinks(sinkClassName) - sender
        if (updatedSinksForClass.isEmpty) {
          activeSinks -= sinkClassName
          completeSources(sinkClassName)
        } else {
          activeSinks += (sinkClassName -> updatedSinksForClass)
        }
        classForSink -= sink
      }
      if (isCompleted) notifyCompleted()
    case AwaitCompleted =>
      awaitingIdle = sender :: awaitingIdle
      if (isCompleted) notifyCompleted()
  }

  /**
   * Returns true if all sinks and sources have been completed.
   */
  private[this] def isCompleted = {
    activeSources.isEmpty && activeSinks.isEmpty
  }

  private[this] def completeSources(sourceClassName: String) = {
    activeSources(sourceClassName).foreach(_ ! Complete)
  }

  private[this] def notifyCompleted() {
    awaitingIdle.foreach(_ ! Completed)
    awaitingIdle = Nil
  }

}
