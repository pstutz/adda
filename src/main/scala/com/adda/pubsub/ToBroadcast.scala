package com.adda.pubsub

final case class ToBroadcast[C <: AnyRef](entity: C)
