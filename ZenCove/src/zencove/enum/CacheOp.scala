package zencove.enum

import spinal.core._

object CacheOp extends SpinalEnum {
  val INDEX_INVALIDATE, HIT_INVALIDATE, FILL_OR_HIT_WRITEBACK_INVALIDATE = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding")(
    INDEX_INVALIDATE -> 0,
    HIT_INVALIDATE -> 4,
    FILL_OR_HIT_WRITEBACK_INVALIDATE -> 5
  )
}
