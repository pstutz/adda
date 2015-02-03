package com.adda.adapters

import org.openrdf.query.QueryLanguage
import org.openrdf.repository.sail.SailRepository
import org.openrdf.sail.memory.MemoryStore

import com.adda.interfaces.TripleStore
import com.adda.messages.Triple

class SesameAdapter extends TripleStore {

  private[this] val sesame = new SailRepository(new MemoryStore)
  sesame.initialize
  private[this] val valueFactory = sesame.getValueFactory

  /**
   * Executes SPARQL select query `query'.
   *
   * @return an iterator of query results.
   */
  def executeSparqlSelect(query: String): Iterator[String => String] = {
    val c = sesame.getConnection
    val tupleQuery = c.prepareTupleQuery(QueryLanguage.SPARQL, query)
    val customResultIterator = tupleQuery.evaluate
    val standardIterator = new Iterator[String => String] {
      def hasNext = customResultIterator.hasNext
      def next = {
        val n = customResultIterator.next
        (variableName: String) => n.getBinding(variableName).getValue.stringValue
      }
    }
    // TODO: Need to materialize in order to be able to close the connection. Better solution to close when `hasNext' is false?
    // This would have the drawback that resources are only released if all items are read.
    val materializedIterator = standardIterator.toList.iterator
    c.close
    materializedIterator
  }

  /**
   * Add a triple `t' to the store.
   */
  def addTriple(t: Triple) {
    val c = sesame.getConnection
    val s = valueFactory.createURI(t.s)
    val p = valueFactory.createURI(t.p)
    if (t.o.startsWith("http://")) {
      val o = valueFactory.createURI(t.o)
      c.add(s, p, o)
    } else {
      val o = valueFactory.createLiteral(t.o)
      c.add(s, p, o)
    }
    c.close
  }

  /**
   * Adds all triples in the iterator. By default delegates to addTriple,
   * but using this call allows the implementation to optimize bulk-loading.
   */
  override def addTriples(triples: Iterator[Triple]) {
    val c = sesame.getConnection
    for (t <- triples) {
      val s = valueFactory.createURI(t.s)
      val p = valueFactory.createURI(t.p)
      if (t.o.startsWith("http://")) {
        val o = valueFactory.createURI(t.o)
        c.add(s, p, o)
      } else {
        val o = valueFactory.createLiteral(t.o)
        c.add(s, p, o)
      }
    }
    c.close
  }

  def shutdown() {
    sesame.shutDown()
  }

}
