package zencove.enum

import spinal.core.SpinalEnum

object RegWriteAddr extends SpinalEnum {
  val NONE = newElement()
  val RT, RD = newElement()
  val R31 = newElement()
}
