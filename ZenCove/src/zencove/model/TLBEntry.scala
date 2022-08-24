package zencove.model

import spinal.core._
import zencove.TLBConfig

final case class TLBEntry(config: TLBConfig) extends Bundle {
  val mask = config.useMask generate Bits(config.maskWidth bits)
  val hi = TLBEntryHi(config)
  val lo = Vec(TLBEntryLo(config), 2)
}
