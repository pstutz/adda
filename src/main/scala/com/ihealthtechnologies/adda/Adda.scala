/**
 * Copyright (C) 2015 Cotiviti Labs (nexgen.admin@cotiviti.io)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ihealthtechnologies.adda

import scala.concurrent.{ Await, Future }
import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

import com.ihealthtechnologies.adda.interfaces.PubSub
import com.ihealthtechnologies.adda.pubsub.{ AwaitCompleted, Broadcaster, CreatePublisher, CreateSubscriber }

import akka.actor.{ ActorRef, ActorSystem, Props }
import akka.event.Logging
import akka.pattern.ask
import akka.stream.ActorMaterializer
import akka.stream.actor.{ ActorPublisher, ActorSubscriber }
import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout

/**
 * Adda implements simple publish/subscribe for objects sent via Akka Streams.
 *
 * The handlers in `privilegedHandlers' get called on all published entities
 * and they are guaranteed to finish running before the entity is passed on to the subscribers.
 *
 * Adda automatically completes all sources for a class, when the number of tracked publishers
 * for this class was > 0, and then falls back to 0 due to stream completions.
 *
 * PubSub cycles are possible, but in this case the stream completion propagation does not work.
 */
class Adda(
    private[this] val privilegedHandlers: List[Any => Unit] = Nil,
    val maxPublisherQueueSize: Int = 100,
    implicit val system: ActorSystem = ActorSystem(Adda.defaultSystemName)) extends PubSub {

  private[this] val broadcasterForTopic = collection.mutable.Map.empty[String, ActorRef]

  implicit val materializer = ActorMaterializer()
  implicit val executor = system.dispatcher
  private[this] val log = Logging.getLogger(system.eventStream, Adda.defaultSystemName)

  def subscribe[C: ClassTag]: Source[C, Unit] = {
    createSubscriptionSource[C]
  }

  def publish[C: ClassTag](trackCompletion: Boolean = true): Sink[C, Unit] = {
    createPublicationSink[C](trackCompletion)
  }

  def awaitCompleted(implicit timeout: Timeout = Timeout(300.seconds)): Unit = {
    val completedFuture = Future.sequence(broadcasterForTopic.values.map(b => b ? AwaitCompleted))
    Await.result(completedFuture, timeout.duration)
  }

  def shutdown(): Unit = {
    log.debug("Shutting down Adda ...")
    system.shutdown()
    system.awaitTermination()
    log.debug("Adda has shut down.")
  }

  private[this] def topic[C: ClassTag]: String = {
    implicitly[ClassTag[C]].runtimeClass.getName
  }

  private[this] def broadcaster(topic: String): ActorRef = {
    broadcasterForTopic.get(topic) match {
      case Some(b) => b
      case None    => createBroadcasterForTopic(topic)
    }
  }

  private[this] def createBroadcasterForTopic(topic: String): ActorRef = {
    val broadcaster = system.actorOf(Props(new Broadcaster(privilegedHandlers)), topic)
    broadcasterForTopic += topic -> broadcaster
    broadcaster
  }

  /**
   * To create a publisher we also need to create a subscriber that connects with it from inside Adda.
   */
  private[this] def createSubscriptionSource[C: ClassTag]: Source[C, Unit] = {
    implicit val timeout = Timeout(5.seconds)
    val t = topic[C]
    val b = broadcaster(t)
    val subscriberActorFuture = b ? new CreateSubscriber[C]()
    // To create the source we need to create an actor publisher that connects the source with the Adda subscriber.
    val sourceFuture = subscriberActorFuture
      .map(p => ActorPublisher[C](p.asInstanceOf[ActorRef]))
      .map(Source.fromPublisher(_))
    Await.result(sourceFuture, 5.seconds)
  }

  private[this] def createPublicationSink[C: ClassTag](trackCompletion: Boolean): Sink[C, Unit] = {
    implicit val timeout = Timeout(5.seconds)
    val t = topic[C]
    val b = broadcaster(t)
    val publisherActorFuture = b ? new CreatePublisher(trackCompletion, maxPublisherQueueSize)
    // To create the sink we need to create an actor subscriber that connects the sink with the Adda publisher.
    val sinkFuture = publisherActorFuture
      .map(s => ActorSubscriber[C](s.asInstanceOf[ActorRef]))
      .map(Sink.fromSubscriber(_))
    Await.result(sinkFuture, 5.seconds)
  }

}

object Adda {
  val defaultSystemName = "Adda"
}
