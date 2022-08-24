package zencove.model

import spinal.core._
import spinal.lib._
import zencove.util._
import zencove.FrontendConfig

/** IF给出的一条指令的信息。存在fetch buffer中。
  */
final case class InstBufferEntry(config: FrontendConfig) extends Bundle {
  val pc = UWord()
  val inst = BWord()
  // 优化宽度，前端exception不需要bad va，永远是pc
  val except = Flow(ExceptionPayload(false))
  val predInfo = BranchPredictInfo()
  val predRecover = PredictRecoverBundle(config)
}
