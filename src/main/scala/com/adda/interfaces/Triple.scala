package com.adda.interfaces

//TODO: Move to stream store.
/**
 * Triple represents a triple with a subject, a predicate, and an object.
 * Such a triple represents a graph fragment with the subject as the ID/label of the source vertex,
 * the predicate as the ID/label of a directed edge, and the object as the ID/label of the target vertex.
 *
 * This is not an RDF triple, as RDF triples have the constraint that both `s' and `p'
 * need to be valid IRIs, whilst `o' can be either a valid IRI or a literal. Triple does not
 * have this constraint, everything is a simple Scala string.
 *
 * When interacting with an RDF triple store this is implicitly resolved with the following heuristic:
 *   - `s' and `p' are always assumed to be valid IRIs.
 *   - `o' is an IRI, iff it starts with "http://", else it is a literal.
 * This heuristic fails in some special cases, for example for an IRI that starts with "mailto:",
 * but for now being able to handle these cases is not worth the added complexity.
 */
final case class Triple(s: String, p: String, o: String)
