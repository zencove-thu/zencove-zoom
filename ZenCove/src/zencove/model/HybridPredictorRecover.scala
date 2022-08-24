package zencove.model

import spinal.core._
import zencove.BPUConfig

final case class HybridPredictorRecover(config: BPUConfig) extends Bundle {
  val selectCounter = config.counterType()
  val localCounter = config.counterType()
  val globalCounter = config.counterType()
  val history = config.historyType()
}
