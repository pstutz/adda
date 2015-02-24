package com.adda.pubsub

import scala.reflect.ClassTag

final case class ToBroadcast[C: ClassTag](entity: C) extends Tagged[C]
