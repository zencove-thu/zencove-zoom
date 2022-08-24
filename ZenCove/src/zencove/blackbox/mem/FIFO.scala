package zencove.blackbox.mem

import spinal.core._
import spinal.lib._

class FwftFifo[T <: Data](dataType: HardType[T], depth: Int) extends Component {
  val addrWidth = log2Up(depth)
  val fifoGeneric = new xpm_fifo_sync_generic
  fifoGeneric.FIFO_WRITE_DEPTH = depth
  fifoGeneric.WRITE_DATA_WIDTH = dataType.getBitsWidth
  fifoGeneric.READ_DATA_WIDTH = dataType.getBitsWidth
  fifoGeneric.WR_DATA_COUNT_WIDTH = addrWidth
  fifoGeneric.RD_DATA_COUNT_WIDTH = addrWidth
  fifoGeneric.READ_MODE = "fwft"
  fifoGeneric.FIFO_READ_LATENCY = 0
  val io = new Bundle {
    val push = slave Stream (dataType)
    val pop = master Stream (dataType)
    val flush = in Bool () default (False)
  }
  val fifo = new xpm_fifo_sync(fifoGeneric)
  fifo.io.wr_en := io.push.valid
  io.push.ready := !fifo.io.full
  fifo.io.din := io.push.payload.asBits
  fifo.io.rd_en := io.pop.ready
  io.pop.payload.assignFromBits(fifo.io.dout)
  io.pop.valid := !fifo.io.empty
}
