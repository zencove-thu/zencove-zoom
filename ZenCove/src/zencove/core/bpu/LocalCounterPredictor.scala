package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model._
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.BPUConfig

/** Basic predictor with saturated counter indexed by PC.
  *
  * @param config
  * @param fetchWidth
  */
class LocalCounterPredictor(config: BPUConfig, fetchWidth: Int) extends Component {
  val counterType = HardType(UInt(config.counterWidth bits))
  val io = new Bundle {
    val read = new Bundle {
      val nextPC = in(UWord())
      val predCounters = out(Vec(counterType, fetchWidth))
      val enable = in(Bool)
    }
    val write = in(Flow(new Bundle {
      val pc = UWord()
      val newCounter = counterType()
    }))
  }
  val pht = new ReorderCacheRAM(counterType, config.sets, fetchWidth, false)
  pht.io.read.cmd.valid := io.read.enable
  pht.io.read.cmd.payload := io.read.nextPC(config.indexRange)
  io.read.predCounters := pht.io.read.rsp
  pht.io.write.valid := io.write.valid
  pht.io.write.mask := 1
  pht.io.write.address := io.write.pc(config.indexRange)
  pht.io.write.data.assignDontCare()
  pht.io.write.data(0) := io.write.newCounter
}
