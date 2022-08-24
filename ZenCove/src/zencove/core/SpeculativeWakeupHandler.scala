package zencove.core

import zencove.builder.Plugin
import spinal.core._

class SpeculativeWakeupHandler extends Plugin[CPU] {
  val wakeupFailed = False
  val regWakeupFailed = RegNext(wakeupFailed, init = False)
  override def build(pipeline: CPU): Unit = {}
}
