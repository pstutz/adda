/**
 * Copyright (C) ${project.inceptionYear} Mycila (mathieu.carbou@gmail.com)
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
/*
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package com.ihealthtechnologies.adda.interfaces

import scala.concurrent.duration.DurationInt
import scala.reflect.ClassTag

import akka.stream.scaladsl.{ Sink, Source }
import akka.util.Timeout

/**
 * An interface that allows to connect Akka Streams with Pub/Sub sinks and sources.
 *
 * All messages routed into a sink for a type have to be of the correct type.
 * The published messages are routed to all the sources that subscribed the this type.
 *
 * Completion of publishers is tracked and all subscribers for a type are completed
 * when the number of tracked publishers for a type was > 0, and then falls back to 0.
 */
trait PubSub {

  /**
   * Returns an Akka Streams source that is subscribed to objects that are published to sinks of type `C'.
   */
  def subscribe[C: ClassTag]: Source[C, Unit]

  /**
   * Returns an Akka Streams sink that allows to publish objects of type `C'.
   *
   * The pubsub system tracks completion for this publisher and completes all subscribers for a type,
   * when the number of tracked publishers for this type was > 0, and then falls back to 0.
   */
  def publish[C: ClassTag]: Sink[C, Unit] = publish[C](trackCompletion = true)

  /**
   * Returns an Akka Streams sink that allows to publish objects of type `C'.
   *
   * The `trackCompletion' parameter determines if the pubsub system should track the completion of this publisher.
   *
   * The pubsub system completes all subscribers for a type, when the number of tracked publishers
   * for this type was > 0, and then falls back to 0.
   */
  def publish[C: ClassTag](trackCompletion: Boolean): Sink[C, Unit]

  /**
   * Blocking call that returns once all the publishers and subscribers have completed.
   *
   * The pubsub system completes all subscribers for a type, when the number of tracked publishers
   * for this type was > 0, and then falls back to 0.
   */
  def awaitCompleted(implicit timeout: Timeout = Timeout(300.seconds)): Unit

  /**
   * Blocking call that shuts down the pub/sub infrastructure and returns when the shutdown is completed.
   */
  def shutdown(): Unit

}
