package com.ihtech.adda.interfaces

import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout

trait PubSub {

  /**
   * Returns an Akka Streams source that is subscribed to all published objects of class `C'.
   */
  def subscribe[C: ClassTag]: Source[C, Unit]

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   *
   * The pubsub system tracks completion for this publisher and completes all subscribers for a topic,
   * when the number of tracked publishers for this class was > 0, and then falls back to 0.
   */
  def publish[C: ClassTag]: Sink[C, Unit] = publish[C](trackCompletion = true)

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   *
   * The `trackCompletion' parameter determines if the pubsub system should track the completion of this publisher.
   *
   * The pubsub system completes all subscribers for a topic, when the number of tracked publishers
   * for this class was > 0, and then falls back to 0.
   */
  def publish[C: ClassTag](trackCompletion: Boolean): Sink[C, Unit]

  /**
   * Blocking call that returns once all the publishers and subscribers have completed.
   *
   * The pubsub system completes all subscribers for a topic, when the number of tracked publishers
   * for this class was > 0, and then falls back to 0.
   */
  def awaitCompleted(implicit timeout: Timeout = Timeout(300.seconds)): Unit

  /**
   * Blocking call that shuts down the pub/sub infrastructure and returns when the shutdown is completed.
   */
  def shutdown(): Unit

}
