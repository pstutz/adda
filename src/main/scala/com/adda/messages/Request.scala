package com.adda.messages

sealed trait Request

//TODO: Move to stream store. 
final case class AddTriples(ts: Set[Triple]) extends Request

//final case class Subscribe(selectQuery: SparqlSelectQuery) extends Request
