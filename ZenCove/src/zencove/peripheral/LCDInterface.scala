package zencove.peripheral

import spinal.core._
import spinal.lib._
import zencove.util.XTriStateArray

final case class LCDInterface() extends Bundle with IMasterSlave {
  val nrst, csel, rs, wr, rd = Bool()
  val data = XTriStateArray(16)

  def asMaster(): Unit = {
    out(nrst, csel, rs, wr, rd)
    master(data)
  }
}
