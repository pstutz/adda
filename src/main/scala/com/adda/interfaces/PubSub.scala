package com.adda.interfaces

import akka.actor.ActorRef
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import scala.reflect.ClassTag

trait PubSub {
  
  def subscribeToSource[C: ClassTag]: Source[C]

  def subscribeToSource[C: ClassTag](actorPublisher: ActorRef): Source[C]

  def getPublicationSink[C]: Sink[C]

}
