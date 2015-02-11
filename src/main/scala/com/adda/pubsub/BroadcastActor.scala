package com.adda.pubsub

import akka.stream.actor.ActorPublisherMessage.Cancel
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag
import com.adda.interfaces.GraphSerializable
import com.adda.interfaces.TripleStore
import akka.actor.Actor
import akka.actor.ActorLogging
import akka.actor.Props
import akka.actor.actorRef2Scala
import akka.util.Timeout
import akka.actor.ActorRef
import akka.actor.Terminated
import akka.event.LoggingReceive

final case class RegisterSink(sinkClassName: String)

final case class RemoveSink(sinkClassName: String)

final case object AwaitCompleted

final case object Completed

final case class CreatePublisher[C: ClassTag]() {
  val props = Props(new SourceActor[C]())
  val className = implicitly[ClassTag[C]].runtimeClass.getName
}

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
      val source = context.actorOf(c.props)
      context.watch(source)
      val sourceClassName = c.className
      val updatedSourcesForClass = activeSources(sourceClassName) + source
      activeSources += (c.className -> updatedSourcesForClass)
      classForSource += (source -> sourceClassName)
      sender ! source
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
    case RegisterSink(sinkClassName) =>
      val updatedSinksForClass = activeSinks(sinkClassName) + sender
      activeSinks += (sinkClassName -> updatedSinksForClass)
      classForSink += (sender -> sinkClassName)
    case RemoveSink(sinkClassName) =>
      val sink = sender
      val updatedSinksForClass = activeSinks(sinkClassName) - sender
      if (updatedSinksForClass.isEmpty) {
        activeSinks -= sinkClassName
        completeSources(sinkClassName)
      } else {
        activeSinks += (sinkClassName -> updatedSinksForClass)
      }
      classForSink -= sink
      if (isCompleted) notifyCompleted()
    case Terminated(source) =>
      val sourceClassName = classForSource(source)
      val updatedSourcesForClass = activeSources(sourceClassName) - source
      if (updatedSourcesForClass.isEmpty) {
        activeSources -= sourceClassName
      } else {
        activeSources += (sourceClassName -> updatedSourcesForClass)
      }
      classForSource -= source
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
  }

}
