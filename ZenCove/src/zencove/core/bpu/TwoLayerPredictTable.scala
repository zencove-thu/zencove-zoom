package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model._
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.BPUConfig
import zencove.blackbox.mem.SDPRAM

class TwoLayerPredictTable(config: BPUConfig, fetchWidth: Int) extends Component {
  val counterType = HardType(UInt(config.counterWidth bits))
  val historyType = HardType(UInt(config.historyWidth bits))
  val phtIndexWidth = log2Up(config.phtSets)
  val phtPCRange = 2 until 2 + phtIndexWidth - config.historyWidth

  val io = new Bundle {
    val read = new Bundle {
      val histories = in(Vec(historyType, fetchWidth))
      val pc = in(UWord())
      val predCounters = out(Vec(counterType, fetchWidth))
      val enable = in Bool ()
    }

    val write = in(Flow(new Bundle {
      val history = historyType()
      val pc = UWord()
      val newCounter = counterType()
    }))
  }
  // TODO: 没有bank conflict，可以banking实现
  val predictTables =
    Seq.fill(fetchWidth)(new SDPRAM(counterType, config.phtSets, false))
  val memInit = Seq.fill(config.phtSets)(1)
  predictTables.foreach(_.memGeneric.MEMORY_INIT_PARAM = memInit.mkString(","))

  for (i <- 0 until fetchWidth) {
    predictTables(i).io.read.cmd.valid := io.read.enable
    predictTables(i).io.read.cmd.payload := io.read.histories(i) @@ (io.read.pc(phtPCRange) + i)
    io.read.predCounters(i) := predictTables(i).io.read.rsp
    predictTables(i).io.write.payload.address := io.write.history @@ io.write.pc(phtPCRange)
    predictTables(i).io.write.payload.data := io.write.newCounter
    predictTables(i).io.write.valid := io.write.valid
  }
}
