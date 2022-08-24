package zencove.enum

import spinal.core.SpinalEnum

object PrivMode extends SpinalEnum {
  val KERNEL, USER = newElement()
}
