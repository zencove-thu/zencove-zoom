package zencove.core

import spinal.core._
import spinal.lib._
import zencove.CacheBasicConfig

class DirtyBitsManager(config: CacheBasicConfig) extends Component {
  val io = new Bundle {
    val readCmd = in(UInt(config.indexWidth bits))
    val readRsp = out(Bits(config.ways bits))
    val writeCmd = in(Flow(new Bundle {
      val idx = UInt(config.indexWidth bits)
      val way = UInt(log2Up(config.ways) bits)
      val data = Bool
    }))
  }
  val dirtyBits = Vec(RegInit(B(0, config.ways bits)), config.sets)
  // normal read
  io.readRsp := dirtyBits(io.readCmd)
  // normal write
  when(io.writeCmd.valid) {
    dirtyBits(io.writeCmd.idx)(io.writeCmd.way) := io.writeCmd.data
  }
  // write -> read bypass
  when(io.writeCmd.valid && io.readCmd === io.writeCmd.idx) {
    io.readRsp(io.writeCmd.way) := io.writeCmd.data
  }
}
