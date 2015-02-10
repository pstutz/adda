package com.adda.examples

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.scalatest.Finders
import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.adda.Adda
import com.adda.interfaces.GraphSerializable
import com.adda.messages.Triple

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source

case class Ping(counter: Int) extends GraphSerializable {
  def asGraph = List(Triple("http://PingCounter", "http://count", counter.toString))
}

case class Pong(counter: Int)

class PingPong extends FlatSpec with Matchers {
  val adda = new Adda
  implicit val system = ActorSystem("Test")
  implicit val materializer = ActorFlowMaterializer()

  val pingPongApp: Flow[Ping, Pong] = Flow[Ping]
    .filter(_.counter >= 99)
    .map { ping => Pong(ping.counter + 1) }

  val pongPingApp: Flow[Pong, Ping] = Flow[Pong].
    map { pong => Ping(pong.counter) }

  Source(List(Ping(0)))
    .runWith(adda.getPublicationSink[Ping])

  adda.subscribeToSource[Ping]
    .via(pingPongApp)
    .runWith(adda.getPublicationSink[Pong])

  adda.subscribeToSource[Pong]
    .via(pongPingApp)
    .runWith(adda.getPublicationSink[Ping])

  val maxPongFuture: Future[Int] = adda.subscribeToSource[Pong]
    .via(pongPingApp)
    .runFold(0)({ case (max, nextPong) => math.max(max, nextPong.counter) })

  val maxPong = Await.result(maxPongFuture, 5.seconds)

  maxPong should be(100)

}
