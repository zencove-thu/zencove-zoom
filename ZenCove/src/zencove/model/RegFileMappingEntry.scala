package zencove.model

import spinal.core._
import zencove.RegFileConfig

final case class RegFileMappingEntry(config: RegFileConfig) extends Bundle {
  val addr = UInt(config.arfAddrWidth bits)
  val prevAddr = UInt(config.prfAddrWidth bits)
  val prfAddr = UInt(config.prfAddrWidth bits)
}
