package zencove.model

import spinal.core._
import spinal.lib._


case class AxiUARTControlBundle() extends Bundle with IMasterSlave {
  val rxd = Bool()
  val txd = Bool()

  override def asMaster(): Unit = {
    out(txd)
    in(rxd)
  }
}