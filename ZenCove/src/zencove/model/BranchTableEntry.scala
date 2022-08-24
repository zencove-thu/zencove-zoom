package zencove.model

import spinal.core._
import zencove.util._
import zencove.BTBConfig

case class BranchTableEntry(config: BTBConfig) extends Bundle {
  val tag = UInt(config.tagRange.size bits)
  // val pred = UInt(2 bits)
  val statusBundle = BranchStatusPayload()
}

case class BranchStatusPayload() extends Bundle {
  val target = UInt(30 bits)
  val isCall = Bool
  val isReturn = Bool
}
