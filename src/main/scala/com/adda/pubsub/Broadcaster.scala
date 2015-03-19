package com.adda.pubsub

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

final case object CanSendNext

final case class CreatePublisher[C: ClassTag]() {
  def createPublisher: AddaSource[C] = new AddaSource[C]()
  val className = implicitly[ClassTag[C]].runtimeClass.getName
}

final case class CreateSubscriber(isTemporary: Boolean) {
  def createSubscriber(broadcaster: ActorRef): AddaSink = new AddaSink(isTemporary, broadcaster)
}

/**
 * Once the number of publishers for the topic was > 0, and then falls back to 0, all
 * subscribers connected with that topic are completed.
 *
 * The `awaitingIdle' list keeps track of actors that are waiting for all processing to complete.
 */
class Broadcaster(
  privilegedHandlers: List[Any => Unit]) extends Actor with ActorLogging with Stash {

  import context._
  implicit val timeout = Timeout(20 seconds)

  def broadcaster(pubSub: PubSubManager): Actor.Receive = LoggingReceive {
    case creationRequest @ CreatePublisher()   => createPublisher(creationRequest, pubSub)
    case creationRequest @ CreateSubscriber(_) => createSubscriber(creationRequest, pubSub)
    case on: OnNext                            => onNext(on, pubSub)
    case bulk: Queue[_]                        => onBulkNext(bulk, pubSub)
    case Terminated(actor)                     => sendingSubscriberIsTerminated(actor, pubSub)
    case AwaitCompleted                        => senderAwaitsCompletion(pubSub)
    case Completed                             => sendingPublisherIsCompleted(pubSub)
  }

  def createPublisher(creationRequest: CreatePublisher[_], pubSub: PubSubManager): Unit = {
    val publisher = context.actorOf(Props(creationRequest.createPublisher))
    watch(publisher)
    sender ! publisher
    become(broadcaster(pubSub.addPublisher(publisher)))
  }

  def createSubscriber(creationRequest: CreateSubscriber, pubSub: PubSubManager): Unit = {
    val subscriber = context.actorOf(Props(creationRequest.createSubscriber(self)))
    sender ! subscriber
    if (!creationRequest.isTemporary) become(broadcaster(pubSub.addSubscriber(subscriber)))
  }

  def onNext(on: OnNext, pubSub: PubSubManager): Unit = {
    val s = sender
    val handlerFuture = Future.sequence(privilegedHandlers.
      map(handler => Future(handler(on.element))))
    handlerFuture.onComplete {
      case Success(_) =>
        pubSub.broadcastToPublishers(on)
        s ! CanSendNext
      case Failure(f) =>
        throw f
    }
  }

  def sendingSubscriberIsTerminated(actor: ActorRef, pubSub: PubSubManager): Unit = {
    val updated = pubSub.removePublisher(actor)
    checkCompletionAndUpdatePubSub(updated)
  }

  def onBulkNext(bulk: Queue[_], pubSub: PubSubManager): Unit = {
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
  }

  def senderAwaitsCompletion(pubSub: PubSubManager): Unit = {
    val updated = pubSub.awaitingCompleted(sender)
    checkCompletionAndUpdatePubSub(updated)
  }

  def sendingPublisherIsCompleted(pubSub: PubSubManager): Unit = {
    val updated = pubSub.removeSubscriber(sender)
    if (updated.subscribers.isEmpty) {
      updated.publishers.foreach(_ ! Complete)
    }
    checkCompletionAndUpdatePubSub(updated)
  }

  def checkCompletionAndUpdatePubSub(pubsub: PubSubManager): Unit = {
    if (pubsub.isCompleted) {
      pubsub.awaitingCompleted.foreach(_ ! Completed)
      become(broadcaster(pubsub.copy(awaitingCompleted = Nil)))
    } else {
      become(broadcaster(pubsub))
    }
  }

  def receive: Actor.Receive = LoggingReceive {
    // Become a broadcaster from the first message on.  
    case anything: Any =>
      stash()
      unstashAll()
      become(broadcaster(PubSubManager()))
  }

}
