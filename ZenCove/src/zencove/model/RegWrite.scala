package zencove.model

import spinal.core._

final case class RegWrite(addrWidth: Int, dataWidth: Int = 32) extends Bundle {
  val addr = UInt(addrWidth bits)
  val data = Bits(dataWidth bits)
}
