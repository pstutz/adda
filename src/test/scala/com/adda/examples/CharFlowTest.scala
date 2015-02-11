package com.adda.examples

import akka.actor.ActorSystem
import akka.stream.ActorFlowMaterializer
import akka.stream.scaladsl.{ForeachSink, Flow, Source}
import com.adda.Adda
import org.scalatest.{FlatSpec, Matchers}
import scala.concurrent.Await
import scala.concurrent.duration.DurationInt

/**
 * Created by jahangirmohammed on 2/11/15.
 */
class CharFlowTest extends FlatSpec with Matchers {

  "Adda" should "take a source of lower case characters and convert them into upper case characters" in {

    val adda = new Adda
    implicit val system = ActorSystem("Test")
    implicit val materializer = ActorFlowMaterializer()

    val lowerCaseToUpperFlow: Flow[Char, Char] = Flow[Char].map(f => {
      f.toUpper
    })

   val pub = adda.getSource[Char]
    
    Source(List('a','d','d','a')).runWith(adda.getSink[Char])

   val futureString =  pub.via(lowerCaseToUpperFlow).runFold(""){(x,y) => x+y}
    
   val futureStringWithoutAdda = Source(List('a','d','d','a')).via(lowerCaseToUpperFlow).runFold(""){(x,y) => x+y}

    val finalStringValue = Await.result(futureStringWithoutAdda, 5.seconds)

    Thread.sleep(5000)

    finalStringValue should be ("ADDA")

    shutdown()

    def shutdown() {
      adda.shutdown()
      system.shutdown()
    }

  }
}
