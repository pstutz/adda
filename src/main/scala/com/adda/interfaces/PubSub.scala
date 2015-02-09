package com.adda.interfaces

import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import scala.reflect.ClassTag

trait PubSub {
  
  def subscribeToSource[C: ClassTag](c: Class[C]): Source[C]

  def getPublicationSink[C]: Sink[C]

}
