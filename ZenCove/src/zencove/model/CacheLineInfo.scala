package zencove.model

import zencove.CacheBasicConfig
import spinal.core._

final case class CacheLineInfo(config: CacheBasicConfig, useDirty: Boolean = false) extends Bundle {
  val lru = Bits(log2Up(config.ways) bits)
  val tags = Vec(UInt(config.tagRange.size bits), config.ways)
  val dirtyBits = useDirty generate Bits(config.ways bits)
}
