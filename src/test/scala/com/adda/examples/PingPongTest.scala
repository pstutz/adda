package com.adda.examples

import scala.concurrent.Await
import scala.concurrent.Future
import scala.concurrent.duration.DurationInt

import org.scalatest.FlatSpec
import org.scalatest.Matchers

import com.adda.Adda

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.Flow
import akka.stream.scaladsl.Source

case class Ping(counter: Int)

case class Pong(counter: Int)

class PingPongTest extends FlatSpec with Matchers {

  "Adda" should "allow two apps play ping pong with each other" in {
    val adda = new Adda
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorFlowMaterializer()

    val pingPongApp: Flow[Ping, Pong] = Flow[Ping]
      .filter(_.counter < 100)
      .map { ping => Pong(ping.counter + 1) }

    val pongPingApp: Flow[Pong, Ping] = Flow[Pong].
      map { pong => Ping(pong.counter) }

    adda.getSource[Ping]
      .via(pingPongApp)
      .runWith(adda.getSink[Pong])

    adda.getSource[Pong]
      .via(pongPingApp)
      .runWith(adda.getSink[Ping])

    val maxPongFuture: Future[Int] = adda.getSource[Pong]
      .runFold(0)({ case (max, nextPong) => math.max(max, nextPong.counter) })

    Source(List(Ping(0)))
      .runWith(adda.getSink[Ping])

    Thread.sleep(100)
    adda.shutdown()

    val maxPong = Await.result(maxPongFuture, 5.seconds)
    maxPong should be(100)
  }

}
