package zencove.model

import spinal.core._
import spinal.lib._
import zencove.util._
import zencove.enum._
import zencove.ZencoveConfig

final case class StoreBufferSlot(config: ZencoveConfig) extends Bundle {
  val retired = Bool
  val addr = UWord()
  val be = Bits(4 bits)
  val data = BWord()
  // extension: accept uncached load/store
  val isStore = Bool
  val isCached = Bool
  val wReg = Flow(UInt(config.regFile.prfAddrWidth bits))
  val lsType = LoadStoreType()
  val robIdx = UInt(config.rob.robAddressWidth bits)
}
