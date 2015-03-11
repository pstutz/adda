package com.adda.pubsub

import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{ Failure, Success }
import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Terminated, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.util.Timeout
import scala.collection.immutable.Queue

final case object AwaitCompleted

final case object Completed

final case object CanSendNext

final case class CreatePublisher[C: ClassTag]() {
  def createPublisher: AddaSource[C] = new AddaSource[C]()
  val className = implicitly[ClassTag[C]].runtimeClass.getName
}

final case class CreateSubscriber(isTemporary: Boolean) {
  def createSubscriber(broadcaster: ActorRef): AddaSink = new AddaSink(isTemporary, broadcaster)
}

/**
 * Once the number of non-completed sinks for a class was > 0, and then falls back to 0, all
 * sources connected with that class are completed.
 *
 * The `awaitingIdle' list keeps track of actors that are waiting for all processing to complete.
 */
class Broadcaster(
  privilegedHandlers: List[Any => Unit]) extends Actor with ActorLogging {
  implicit val timeout = Timeout(20 seconds)
  implicit val executor = context.system.dispatcher
  val pubSub = new PubSubManager

  def receive: Actor.Receive = LoggingReceive {
    case c @ CreatePublisher() =>
      val publisher = createPublisher(c)
      sender ! publisher
    case c @ CreateSubscriber(_) =>
      val subscriber = createSubscriber(c)
      sender ! subscriber
    case on @ OnNext(e) =>
      val s = sender
      val handlerFuture = Future.sequence(privilegedHandlers.
        map(handler => Future(handler(e))))
      handlerFuture.onComplete {
        case Success(_) =>
          pubSub.broadcastToPublishers(on)
          s ! CanSendNext
        case Failure(f) =>
          throw f
      }
    case bulk: Queue[_] =>
      val s = sender
      val handlerFuture = Future.sequence(privilegedHandlers.
        map(handler => Future(bulk.map(handler))))
      handlerFuture.onComplete {
        case Success(_) =>
          pubSub.bulkBroadcastToPublishers(bulk)
          s ! CanSendNext
        case Failure(f) =>
          throw f
      }
    case Terminated(actor) =>
      pubSub.removePublisher(actor)
    case AwaitCompleted =>
      pubSub.awaitingCompleted(sender)
    case Completed =>
      pubSub.removeSubscriber(sender)
  }

  def createPublisher[C](c: CreatePublisher[C]): ActorRef = {
    val publisher = context.actorOf(Props(c.createPublisher))
    context.watch(publisher)
    pubSub.addPublisher(publisher)
    publisher
  }

  def createSubscriber[C](c: CreateSubscriber): ActorRef = {
    val subscriber = context.actorOf(Props(c.createSubscriber(self)))
    if (!c.isTemporary) pubSub.addSubscriber(subscriber)
    subscriber
  }

}
