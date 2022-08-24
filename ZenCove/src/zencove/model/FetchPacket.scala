package zencove.model

import spinal.core._
import spinal.lib._
import zencove.util._

/** I-cache 一个周期给出的内容。
  *
  * @param fetchWidth
  */
final case class FetchPacket(fetchWidth: Int) extends Bundle {
  val pc = UWord()
  val insts = Vec(Flow(BWord()), fetchWidth)
  // 优化宽度，前端exception不需要bad va，永远是pc
  val except = Flow(ExceptionPayload(false))
}
