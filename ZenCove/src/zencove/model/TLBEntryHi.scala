package zencove.model

import spinal.core._
import zencove.TLBConfig

final case class TLBEntryHi(config: TLBConfig) extends Bundle {
  val ASID = Bits(config.asidWidth bits)
  val G = Bool()
  val VPN2 = UInt(config.vpnWidth - 1 bits)
  // val R = Bool()
}
