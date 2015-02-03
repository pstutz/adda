package com.adda.messages

import scala.util.Try

sealed trait Request

//TODO: Move to stream store. 
final case class AddTriples(ts: Set[Triple]) extends Request

//final case class Subscribe(selectQuery: SparqlSelectQuery) extends Request
