package com.adda.interfaces

import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import akka.util.Timeout

trait PubSub {

  /**
   * Returns an Akka Streams source that is subscribed to all published objects of class `C'.
   */
  def createSource[C: ClassTag]: Source[C, Unit]

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   */
  def createSink[C: ClassTag]: Sink[C, Unit]

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   *
   * The difference to `createSink' is that this sink is expected to complete soon,
   * and will never propagate the completion to the sources that subscribe to the class.
   */
  def createTemporarySink[C: ClassTag]: Sink[C, Unit]

  /**
   * Blocking call that returns once all the incoming sinks have completed and all the sources
   * have published the remaining items.
   *
   * Adda automatically completes all sources for a class, when the number of active sinks
   * for this class was > 0, and then falls back to 0.
   */
  def awaitCompleted()(implicit timeout: Timeout = Timeout(60.seconds)): Unit

  /**
   * Blocking call that shuts down the pub/sub infrastructure and returns when the shutdown is completed.
   */
  def shutdown(): Unit

}
