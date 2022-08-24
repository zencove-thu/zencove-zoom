package zencove.enum

import spinal.core.SpinalEnum

object HiLoWriteType extends SpinalEnum {
  val NONE = newElement()
  val WRITE, ADD, SUB = newElement()
}
