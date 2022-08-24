package zencove.model

import spinal.core._
import zencove.util.UWord
import zencove.FrontendConfig

/** 用于更新BTB。
  */
final case class PredictUpdate(config: FrontendConfig) extends Bundle {
  // 原先predict信息
  val predInfo = BranchPredictInfo(false)
  val predRecover = PredictRecoverBundle(config)
  // 实际branch计算信息
  val branchLike = Bool
  val isTaken = Bool
  val isRet = Bool
  val isCall = Bool
  val mispredict = Bool
  val pc = UWord()
  // 这里永远记录branch target，顺序target通过PC恢复
  val target = UWord()
}
