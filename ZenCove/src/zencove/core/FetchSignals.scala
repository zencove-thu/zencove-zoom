package zencove.core

import zencove.ZencoveConfig
import zencove.builder.Stageable
import spinal.core._
import spinal.lib._
import zencove.enum._
import zencove.model._

class FetchSignals(config: ZencoveConfig) {
  private val fetchWidth = config.frontend.fetchWidth
  object FETCH_PACKET extends Stageable(FetchPacket(fetchWidth))
  object HYBRID_RECOVERS extends Stageable(Vec(HybridPredictorRecover(config.frontend.bpu), fetchWidth))
  object INSTRUCTION_MASK extends Stageable(Bits(fetchWidth bits))
  object BRANCH_MASK extends Stageable(Bits(fetchWidth bits))
  object TAKEN_MASK extends Stageable(Bits(fetchWidth bits))
  object PRED_COUNTER
      extends Stageable(Vec(UInt(config.frontend.bpu.counterWidth bits), fetchWidth))
  object RECOVER_TOP extends Stageable(UInt(log2Up(config.frontend.btb.rasEntries) bits))
  object GLOBAL_BRANCH_HISTORY extends Stageable(UInt(config.frontend.bpu.historyWidth bits))
  object PRIVATE_BRANCH_HISTORY 
      extends Stageable(Vec(UInt(config.frontend.bpu.historyWidth bits), fetchWidth))
  object PREDICT_JUMP_FLAG extends Stageable(Bool)
  object PREDICT_JUMP_PAYLOAD extends Stageable(BranchStatusPayload())
  object PREDICT_JUMP_WAY extends Stageable(UInt(log2Up(config.frontend.fetchWidth) bits))
}
