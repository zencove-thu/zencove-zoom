package zencove.core

import zencove.ZencoveConfig
import zencove.builder.Stageable
import spinal.core._
import spinal.lib._
import zencove.enum._
import zencove.model._
import zencove.util._

class MemSignals(config: ZencoveConfig) {
  private val dcache = config.dcache
  private val iqDepth = config.memIssue.depth
  private val rPorts = config.regFile.rPortsEachInst
  private val prfAddrWidth = config.regFile.prfAddrWidth
  object ISSUE_SLOT extends Stageable(MemIssueSlot(config))
  object REG_READ_RSP extends Stageable(Vec(BWord(), rPorts))
  object WRITE_REG extends Stageable(Flow(UInt(prfAddrWidth bits)))
  object READ_REGS extends Stageable(Vec(UInt(prfAddrWidth bits), 2))
  object ROB_IDX extends Stageable(UInt(config.rob.robAddressWidth bits))
  object STD_SLOT extends Stageable(Flow(StoreBufferSlot(config)))
}
