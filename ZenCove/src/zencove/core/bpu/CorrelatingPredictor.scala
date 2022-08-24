package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model._
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.FrontendConfig

/** (m,n) correlating predictor with both GHR and PC indexing PHT.
  *
  * @param config
  * @param fetchWidth
  */
class CorrelatingPredictor(config: FrontendConfig, fetchWidth: Int) extends Component {
  val counterType = HardType(UInt(config.bpu.counterWidth bits))
  val ghrType = HardType(UInt(config.bpu.historyWidth bits))
  val io = new Bundle {
    val read = new Bundle {
      val enable = in(Bool)
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
    config.bpu.phtSets,
    fetchWidth,
    false
  )

  val memInit = Seq.fill(pht.wordsPerBank)(1)
  pht.rams.foreach(_.memGeneric.MEMORY_INIT_PARAM = memInit.mkString(","))

  io.read.globalHistory := ghr
  when(io.updateGHR.valid) { ghr := io.updateGHR.payload }
  nextGHR := Mux(io.updateGHR.valid, io.updateGHR.payload, ghr)

  pht.io.read.cmd.valid := io.read.enable
  pht.io.read.cmd.payload := nextGHR @@ io.read.nextPC(config.bpu.phtPCRange)
  io.read.predCounters := pht.io.read.rsp
  for (i <- 0 until fetchWidth) io.read.predTaken(i) := io.read.predCounters(i).msb

  pht.io.write.valid := io.write.valid
  pht.io.write.mask := 1
  pht.io.write.address := io.write.ghr @@ io.write.pc(config.bpu.phtPCRange)
  pht.io.write.data.assignDontCare()
  pht.io.write.data(0) := io.write.newCounter
}
