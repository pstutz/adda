package com.adda.interfaces

import com.adda.messages.Triple

trait GraphSerializable {

  def asGraph: List[Triple]
  
}
