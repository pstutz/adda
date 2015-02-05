package com.adda

import com.adda.interfaces.SparqlSource
import com.adda.interfaces.TripleStreamStore
import akka.stream.scaladsl.Source
import com.adda.adapters.SesameAdapter
import com.adda.interfaces.TripleStore

class Adda extends SparqlSource with TripleStreamStore {

  private[this] val store: TripleStore = new SesameAdapter

  def subscribe(sparqlSelect: String): Source[String => String] = {
    Source(List({ s: String => "Hello World!" }))
  }

}
