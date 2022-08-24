package zencove.model

import spinal.core._
import spinal.lib.IMasterSlave

case class AxiSramControlBundle() extends Bundle with IMasterSlave {
  val addr = Bits(32 bits)
  val ben = Bits(4 bits)
  val ce_n = Bool()
  val dq_i = Bits(32 bits)
  val dq_o = Bits(32 bits)
  val dq_t = Bits(32 bits)
  val oen = Bool()
  val wen = Bool()

  override def asMaster(): Unit = {
    out(addr, ben, ce_n, dq_o, dq_t, oen, wen)
    in(dq_i)
  }
}
