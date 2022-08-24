package zencove.core

import zencove.builder.Plugin
import spinal.core._

class WaitHandler extends Plugin[CPU] {
  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    val inLowPowerMode = RegInit(False)
    inLowPowerMode setWhen pipeline.service[WaitCommit].doWait
    // inLowPowerMode clearWhen pipeline.service[IntStatusProvider].intPending
    inLowPowerMode clearWhen pipeline.service[InterruptHandler].IP.orR
    pipeline.IF1.arbitration.haltByOther setWhen inLowPowerMode
  }
}
