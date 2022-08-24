package zencove.model

import spinal.core._
import zencove.TLBConfig

final case class TLBEntryLo(config: TLBConfig) extends Bundle {
  val V, D = Bool()
  val C = Bits(3 bits)
  val PFN = UInt(config.pfnWidth bits)
}
