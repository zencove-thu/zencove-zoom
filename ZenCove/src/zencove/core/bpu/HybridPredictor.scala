package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model._
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.FrontendConfig

/** Saturated counter + Gselect.
  *
  * @param config
  * @param fetchWidth
  */
class HybridPredictor(config: FrontendConfig, fetchWidth: Int) extends Component {
  val counterType = HardType(UInt(config.bpu.counterWidth bits))
  val io = new Bundle {
    val read = new Bundle {
      val enable = in(Bool)
      val nextPC = in(UWord())
      val predInfo = out(Vec(HybridPredictorRecover(config.bpu), fetchWidth))
      val predTaken = out(Bits(fetchWidth bits))
    }
    // speculative update or restore
    val updateGHR = in(Flow(config.bpu.historyType))
    val write = in(Flow(new Bundle {
      val pc = UWord()
      val newInfo = HybridPredictorRecover(config.bpu)
    }))
  }
  val localPredictor = new LocalCounterPredictor(config.bpu, fetchWidth)
  val globalPredictor = new CorrelatingPredictor(config, fetchWidth)
  val metaTable = new ReorderCacheRAM(counterType, config.bpu.sets, fetchWidth, false)

  val memInit = Seq.fill(metaTable.wordsPerBank)(1)
  metaTable.rams.foreach(_.memGeneric.MEMORY_INIT_PARAM = memInit.mkString(","))

  metaTable.io.read.cmd.valid := io.read.enable
  metaTable.io.read.cmd.payload := io.read.nextPC(config.bpu.indexRange)
  localPredictor.io.read.enable := io.read.enable
  localPredictor.io.read.nextPC := io.read.nextPC
  globalPredictor.io.read.enable := io.read.enable
  globalPredictor.io.read.nextPC := io.read.nextPC
  for (i <- 0 until fetchWidth) {
    val selectCounter = metaTable.io.read.rsp(i)
    val localCounter = localPredictor.io.read.predCounters(i)
    val globalCounter = globalPredictor.io.read.predCounters(i)
    io.read.predInfo(i).selectCounter := selectCounter
    io.read.predInfo(i).localCounter := localCounter
    io.read.predInfo(i).globalCounter := globalCounter
    io.read.predInfo(i).history := globalPredictor.io.read.globalHistory
    io.read.predTaken(i) := Mux(selectCounter.msb, globalCounter.msb, localCounter.msb)
  }

  globalPredictor.io.updateGHR := io.updateGHR

  metaTable.io.write.valid := io.write.valid
  metaTable.io.write.mask := 1
  metaTable.io.write.address := io.write.pc(config.bpu.indexRange)
  metaTable.io.write.data.assignDontCare()
  metaTable.io.write.data(0) := io.write.newInfo.selectCounter
  localPredictor.io.write.valid := io.write.valid
  localPredictor.io.write.pc := io.write.pc
  localPredictor.io.write.newCounter := io.write.newInfo.localCounter
  globalPredictor.io.write.valid := io.write.valid
  globalPredictor.io.write.pc := io.write.pc
  globalPredictor.io.write.ghr := io.write.newInfo.history
  globalPredictor.io.write.newCounter := io.write.newInfo.localCounter
}
