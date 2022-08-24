package zencove.enum

import spinal.core.SpinalEnum

object TLBOp extends SpinalEnum {
  val NONE = newElement()
  val PROBE, READ, WRITE_INDEX, WRITE_RANDOM = newElement()
}
