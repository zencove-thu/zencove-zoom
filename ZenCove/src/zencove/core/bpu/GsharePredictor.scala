package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model._
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.BPUConfig

/** GShare. 不太行。
  *
  * @param config
  * @param fetchWidth
  */
class GsharePredictor(config: BPUConfig, fetchWidth: Int) extends Component {
  val offsetWidth = log2Up(fetchWidth)
  val counterType = HardType(UInt(config.counterWidth bits))
  val ghrType = HardType(UInt(config.historyWidth bits))
  val io = new Bundle {
    val read = new Bundle {
      val nextPC = in(UWord())
      val predCounters = out(Vec(counterType, fetchWidth))
      val predTaken = out(Bits(fetchWidth bits))
      val globalHistory = out(ghrType())
    }
    // speculative update or restore
    val updateGHR = in(Flow(ghrType))
    val write = in(Flow(new Bundle {
      val pc = UWord()
      val ghr = ghrType()
      val newCounter = counterType()
    }))
  }
  // global history register
  val ghr = Reg(ghrType) init 0
  val nextGHR = ghrType()
  // pattern history table
  val pht = new ReorderCacheRAM(
    counterType,
    config.sets,
    fetchWidth,
    false
  )

  val memInit = Seq.fill(pht.wordsPerBank)(1)
  pht.rams.foreach(_.memGeneric.MEMORY_INIT_PARAM = memInit.mkString(","))

  io.read.globalHistory := ghr
  when(io.updateGHR.valid) { ghr := io.updateGHR.payload }
  nextGHR := Mux(io.updateGHR.valid, io.updateGHR.payload, ghr)

  pht.io.read.cmd.valid := True
  pht.io.read.cmd.payload := (nextGHR @@ U(0, offsetWidth bits)) ^ io.read.nextPC(config.indexRange)
  io.read.predCounters := pht.io.read.rsp
  for (i <- 0 until fetchWidth) io.read.predTaken(i) := io.read.predCounters(i).msb

  pht.io.write.valid := io.write.valid
  pht.io.write.mask := 1
  pht.io.write.address := (io.write.ghr @@ U(0, offsetWidth bits)) ^ io.write.pc(config.indexRange)
  pht.io.write.data.assignDontCare()
  pht.io.write.data(0) := io.write.newCounter
}
