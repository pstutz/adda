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

final case object CompleteAllPublishers

final case class CreatePublisher[C: ClassTag]() {
  val props = Props(new SourceActor[C]())
  val className = implicitly[ClassTag[C]].runtimeClass.getName
}

class BroadcastActor(private[this] val store: TripleStore) extends Actor with ActorLogging {
  private[this] implicit val timeout = Timeout(20 seconds)

  def receive = {
    case c @ CreatePublisher() =>
      val publisherActor = context.actorOf(c.props)
      sender ! publisherActor
    case a @ AddaEntity(e) =>
      e match {
        // If the entity is graph serializable, add it to the store.
        case g: GraphSerializable =>
          val triples = g.asGraph
          triples.foreach(store.addTriple(_))
        case other => // Do nothing.
      }
      context.children.foreach(_ ! a)
    case CompleteAllPublishers =>
      context.children.map(_ ! Complete)
  }

}
