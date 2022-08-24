package zencove.core

import zencove.builder._

trait MemPipeline extends Pipeline {
  type T = MemPipeline
  val ISS: Stage = null // issue/select
  val RRD: Stage = null // register read
  val MEM1: Stage = null
  val MEM2: Stage = null
  val WB: Stage = null
  val WB2: Stage = null // 注意读cache需要预留两个周期的冲突，所以增加一个空的WB阶段，用来前传
  val signals: MemSignals
}
