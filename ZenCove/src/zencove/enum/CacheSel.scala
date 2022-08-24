package zencove.enum

import spinal.core._

object CacheSel extends SpinalEnum {
  val I, D, T, S = newElement()
  defaultEncoding = SpinalEnumEncoding("staticEncoding")(
    I -> 0,
    D -> 1,
    T -> 2,
    S -> 3
  )
}
