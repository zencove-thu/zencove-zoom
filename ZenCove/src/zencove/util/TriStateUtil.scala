package zencove.util

import spinal.core._
import spinal.lib._
import spinal.lib.io.TriStateArray

/** When T is high, O is in tri-state mode.
  *
  * https://docs.xilinx.com/r/en-US/ug1353-versal-architecture-ai-libraries/IOBUF
  *
  * @param dataType
  */
final case class XTriState[T <: Data](dataType: HardType[T]) extends Bundle with IMasterSlave {
  val i, o: T = dataType()
  val t = Bool()
  override def asMaster(): Unit = {
    out(o, t)
    in(i)
  }
}

final case class XTriStateArray(width: Int) extends Bundle with IMasterSlave {
  val i, o, t = Bits(width bits)

  override def asMaster(): Unit = {
    out(o, t)
    in(i)
  }
}
