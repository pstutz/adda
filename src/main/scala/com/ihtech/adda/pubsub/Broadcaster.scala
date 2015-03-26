package com.ihtech.adda.pubsub

import scala.collection.immutable.Queue
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt
import scala.language.postfixOps
import scala.reflect.ClassTag
import scala.util.{ Failure, Success }

import akka.actor.{ Actor, ActorLogging, ActorRef, Props, Stash, Terminated, actorRef2Scala }
import akka.event.LoggingReceive
import akka.stream.actor.ActorSubscriberMessage.OnNext
import akka.util.Timeout

final case object AwaitCompleted

final case object Completed

final case object CanPublishNext

final case class CreateSubscriber[C: ClassTag]() {
  def createSubscriber: Subscriber[C] = new Subscriber[C]()
  val className = implicitly[ClassTag[C]].runtimeClass.getName
}

final case class CreatePublisher(trackCompletion: Boolean) {
  def createPublisher(broadcaster: ActorRef): Publisher = new Publisher(trackCompletion, broadcaster)
}

/**
 * There is exactly one broadcaster actor per type (topic) and all streamed elements
 * for that type pass through it. For this reason the actual element handling is off-loaded to futures.
 *
 * The broadcaster keeps track of some of the Adda publishers and all of the subscribers. It
 * ensures that elements that arrive from publisher are forwarded to all the subscribers.
 *
 * Once the number of tracked publishers for this topic was > 0, and then falls back to 0, all
 * subscribers connected with it are completed.
 *
 * The `awaitingIdle' list keeps track of actors that are waiting for the subscribers of this type to complete.
 */
class Broadcaster(
  privilegedHandlers: List[Any => Unit]) extends Actor with ActorLogging with Stash {

  implicit val timeout = Timeout(20 seconds)
  implicit val executor = context.dispatcher

  def broadcaster(pubSub: PubSubManager): Actor.Receive = LoggingReceive {
    case creationRequest @ CreateSubscriber() => createSubscriber(creationRequest, pubSub)
    case creationRequest @ CreatePublisher(_) => createPublisher(creationRequest, pubSub)
    case on: OnNext                           => onNext(on, pubSub)
    case bulk: Queue[_]                       => onBulkNext(bulk, pubSub)
    case Terminated(actor)                    => sendingSubscriberIsTerminated(actor, pubSub)
    case AwaitCompleted                       => senderAwaitsCompletion(pubSub)
    case Completed                            => sendingPublisherIsCompleted(pubSub)
  }

  def createSubscriber(creationRequest: CreateSubscriber[_], pubSub: PubSubManager): Unit = {
    val subscriber = context.actorOf(
      Props(creationRequest.createSubscriber),
      s"subscriber${pubSub.nextUniqueActorId}")
    context.watch(subscriber)
    sender ! subscriber
    context.become(broadcaster(pubSub.addSubscriber(subscriber)))
  }

  def createPublisher(creationRequest: CreatePublisher, pubSub: PubSubManager): Unit = {
    val publisher = context.actorOf(
      Props(creationRequest.createPublisher(self)),
      s"publisher${pubSub.nextUniqueActorId}")
    sender ! publisher
    if (creationRequest.trackCompletion) context.become(broadcaster(pubSub.addPublisher(publisher)))
  }

  def onNext(on: OnNext, pubSub: PubSubManager): Unit = {
    val s = sender
    val handlerFuture = Future.sequence(privilegedHandlers.
      map(handler => Future(handler(on.element))))
    handlerFuture.onComplete {
      case Success(_) =>
        pubSub.broadcastToSubscribers(on)
        s ! CanPublishNext
      case Failure(e) =>
        reportHandlerError(e)
    }
  }

  def onBulkNext(bulk: Queue[_], pubSub: PubSubManager): Unit = {
    val s = sender
    val handlerFuture = Future.sequence(privilegedHandlers.
      map(handler => Future(bulk.map(handler))))
    handlerFuture.onComplete {
      case Success(_) =>
        pubSub.bulkBroadcastToSubscribers(bulk)
        s ! CanPublishNext
      case Failure(e) =>
        reportHandlerError(e)
    }
  }

  def sendingSubscriberIsTerminated(actor: ActorRef, pubSub: PubSubManager): Unit = {
    val updated = pubSub.removeSubscriber(actor)
    checkCompletionAndUpdatePubSub(updated)
  }

  def senderAwaitsCompletion(pubSub: PubSubManager): Unit = {
    val updated = pubSub.awaitingCompleted(sender)
    checkCompletionAndUpdatePubSub(updated)
  }

  def sendingPublisherIsCompleted(pubSub: PubSubManager): Unit = {
    val updated = pubSub.removePublisher(sender)
    if (updated.publishers.isEmpty) {
      updated.subscribers.foreach(_ ! Complete)
    }
    checkCompletionAndUpdatePubSub(updated)
  }

  def checkCompletionAndUpdatePubSub(pubsub: PubSubManager): Unit = {
    if (pubsub.isCompleted) {
      pubsub.awaitingCompleted.foreach(_ ! Completed)
      context.become(broadcaster(pubsub.copy(awaitingCompleted = Nil)))
    } else {
      context.become(broadcaster(pubsub))
    }
  }

  def receive: Actor.Receive = LoggingReceive {
    // Become a broadcaster from the first message on.
    case anything: Any =>
      stash()
      unstashAll()
      context.become(broadcaster(PubSubManager()))
  }

  def reportHandlerError(e: Throwable): Unit = {
    log.error(e, s"Handler(s) failed with error ${e.getMessage}.")
  }

}
