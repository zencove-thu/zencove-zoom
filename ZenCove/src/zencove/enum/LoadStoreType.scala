package zencove.enum

import spinal.core._

object LoadStoreType extends SpinalEnum {
  val BYTE, HALF, LEFT, WORD, BYTE_U, HALF_U, RIGHT = newElement()
  def toAxiSize(lsType: SpinalEnumCraft[LoadStoreType.type]): UInt = {
    val size = UInt(3 bits)
    switch(lsType) {
      is(BYTE, BYTE_U) {
        size := 0
      }
      is(HALF, HALF_U) {
        size := 1
      }
      default {
        size := 2
      }
    }
    size
  }
}
