package com.adda.interfaces

trait TripleStore extends SparqlSelect {

  /**
   * Add a triple `t' to the store.
   */
  def addTriple(t: Triple)

  /**
   * Add a triple with subject, predicate and object to the store. By default
   * delegates to the Triple object implementation, but can be overridden to
   * save the Triple wrapper.
   */
  def addTriple(s: String, p: String, o: String) {
    addTriple(Triple(s, p, o))
  }

  /**
   * Adds all triples in the iterator. By default delegates to addTriple,
   * but using this call allows the implementation to optimize bulk-loading.
   */
  def addTriples(triples: Iterator[Triple]) {
    for (t <- triples) {
      addTriple(t)
    }
  }

  /**
   * Shuts down the store.
   */
  def shutdown()

}
