package zencove.model

import spinal.core._
import zencove.RegFileConfig

final case class RenameRecord(config: RegFileConfig) extends Bundle {
  val rRegs = Vec(UInt(config.prfAddrWidth bits), config.rPortsEachInst)
  val wReg = UInt(config.prfAddrWidth bits)
  val wPrevReg = UInt(config.prfAddrWidth bits)
}

final case class ROBRenameRecord(config: RegFileConfig) extends Bundle {
  val wReg = UInt(config.prfAddrWidth bits)
  val wPrevReg = UInt(config.prfAddrWidth bits)
}
