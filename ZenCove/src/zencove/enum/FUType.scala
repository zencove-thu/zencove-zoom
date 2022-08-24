package zencove.enum

import spinal.core.SpinalEnum

/** Function unit type.
  */
object FUType extends SpinalEnum {
  val NONE = newElement()
  val ALU, CMP, CP0 = newElement()
  // HLU = Hi/Lo unit
  val MUL, DIV, HLU = newElement()
  val LSU = newElement()
}
