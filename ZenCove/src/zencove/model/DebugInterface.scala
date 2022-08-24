package zencove.model

import spinal.core._

final case class DebugRegFile() extends Bundle {
  val wen = Bits(4 bits)
  val wnum = UInt(5 bits)
  val wdata = Bits(32 bits)
}

final case class DebugWriteback() extends Bundle {
  val pc = UInt(32 bits)
  val rf = DebugRegFile()
}

final case class DebugInterface() extends Bundle {
  val wb = DebugWriteback()
}
