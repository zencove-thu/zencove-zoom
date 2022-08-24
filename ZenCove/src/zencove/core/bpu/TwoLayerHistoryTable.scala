package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model._
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.BPUConfig

class TwoLayerHistoryTable(config: BPUConfig, fetchWidth: Int) extends Component {
  val historyType = HardType(UInt(config.historyWidth bits))

  val io = new Bundle {
    val read = new Bundle {
      val nextPC = in(UWord())
      val histories = out(Vec(historyType, fetchWidth))
      val enable = in(Bool)
    }

    val write = in(Flow(new Bundle {
      val pc = UWord()
      val newHistory = historyType()
    }))
  }

  val historyTable = new ReorderCacheRAM(historyType, config.sets, fetchWidth, false)
  val memInit = Seq.fill(historyTable.wordsPerBank)(0)
  historyTable.rams.foreach(_.memGeneric.MEMORY_INIT_PARAM = memInit.mkString(","))

  historyTable.io.read.cmd.valid := io.read.enable
  historyTable.io.read.cmd.payload := io.read.nextPC(config.indexRange)
  io.read.histories := historyTable.io.read.rsp
  historyTable.io.write.valid := io.write.valid
  historyTable.io.write.mask := 1
  historyTable.io.write.address := io.write.pc(config.indexRange)
  historyTable.io.write.data.assignDontCare()
  historyTable.io.write.data(0) := io.write.newHistory;

}
