package com.adda.interfaces

import akka.actor.ActorRef
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import scala.reflect.ClassTag

trait PubSub {

  /**
   * Returns an Akka Streams source that is subscribed to all published objects of class `C'.
   * Does not publish subclasses of `C'.
   */
  def subscribeToSource[C: ClassTag]: Source[C]

  /**
   * Returns an Akka Streams sink that allows to publish objects of class `C'.
   * Does not allow publishing subclasses of `C'.
   */
  def getPublicationSink[C]: Sink[C]

  /**
   * Blocking call that returns once the pub/sub infrastructure has shut down.
   * Shutdown closes all stream sources.
   */
  def shutdown()

}
