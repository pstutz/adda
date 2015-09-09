package com.ihealthtechnologies.adda

object DelayingHandler extends Function1[Any, Unit] {
  def apply(a: Any): Unit = {
    Thread.sleep(50)
  }
}
