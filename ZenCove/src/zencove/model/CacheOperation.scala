package zencove.model

import spinal.core._
import zencove.enum._
import zencove.util.UWord

final case class CacheOperation() extends Bundle {
  val addr = UWord()
  val sel = CacheSel()
  val op = CacheOp()
}
