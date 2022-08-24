package zencove.model

import spinal.core._
import zencove.util._
import zencove.FrontendConfig

final case class PredictRecoverBundle(config: FrontendConfig) extends Bundle {
  val recoverTop = UInt(log2Up(config.btb.rasEntries) bits)
  val predictCounter = !config.bpu.useHybrid generate UInt(config.bpu.counterWidth bits)
  val ghr = config.bpu.useGlobal generate UInt(config.bpu.historyWidth bits)
  val hybridRecover = config.bpu.useHybrid generate HybridPredictorRecover(config.bpu)
}
