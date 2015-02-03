package com.adda.interfaces

import akka.stream.scaladsl.Source

trait SparqlSource {
  
  def subscribe(sparqlSelect: String): Source[String => String]
  
}
