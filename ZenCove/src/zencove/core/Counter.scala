package zencove.core

import zencove.builder._
import spinal.core._
import zencove.InterruptConfig

class Counter(config: InterruptConfig) extends Plugin[CPU] {
  private val flip = RegInit(False)
  val regCount = RegInit(U(0, 32 bits))
  val compare = RegInit(U(0, 32 bits))
  val timerInt = RegInit(False)
  override def setup(pipeline: CPU): Unit = {
    val cp0 = pipeline.service[CP0Regs]
    import zencove.MIPS32.CP0
    cp0.rw(CP0.Count, 0 -> regCount)
    cp0.rw(CP0.Compare, 0 -> compare)
    cp0.onWrite(CP0.Compare)(timerInt.clear())
  }
  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    pipeline.service[InterruptHandler].timerInt := timerInt
    // 大赛指令系统规范6.3: Count寄存器每两周期+1
    flip := ~flip
    when(flip) { regCount := regCount + 1 }
    config.withTimerInt generate timerInt.setWhen(regCount === compare)
  }
}
