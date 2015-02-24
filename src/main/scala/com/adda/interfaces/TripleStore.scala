package com.adda.interfaces

trait TripleStore extends SparqlSelect {

  /**
   * Add a triple `t' to the store.
   */
  def addTriple(t: Triple): Unit

  /**
   * Adds all triples in the iterator to the store.
   */
  def addTriples(triples: Iterator[Triple]): Unit

  /**
   * Shuts down the store.
   */
  def shutdown(): Unit

}
