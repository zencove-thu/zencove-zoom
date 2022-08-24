package zencove.core

import spinal.core._
import spinal.lib._
import zencove.builder.Plugin
import zencove.HiLoConfig
import zencove.util.BWord
import zencove.model._

class HiLoRegFile(config: HiLoConfig) extends Plugin[CPU] {
  val regs = Vec(RegInit(B(0, 64 bits)), config.nPhysRegs)
  val writePort = Flow(RegWrite(config.prfAddrWidth, 64))
  val readAddr = UInt(config.prfAddrWidth bits)
  val readRsp = Bits(64 bits)
  override def build(pipeline: CPU): Unit = {
    when(writePort.valid) { regs(writePort.addr) := writePort.data }
    readRsp := regs(readAddr)
    // W->R bypass
    when(writePort.valid && writePort.addr === readAddr) { readRsp := writePort.data }
  }
}
