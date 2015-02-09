package com.adda.interfaces

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source

trait PubSub {
  
  def subscribeToSource[C](c: Class[C]): Source[C]

  def getPublicationSink[C]: Sink[C]

  def getGraphPublicationSink[C <: GraphSerializable]: Sink[C]

}
