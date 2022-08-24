package zencove.peripheral.usb

import spinal.core._
import spinal.lib._
import zencove.util.XTriState

/** UTMI+ low pin interface (ULPI). Not used.
  */
final case class ULPIInterface() extends Bundle with IMasterSlave {
  val clock = Bool // All USB protocol interface signals are synchronous to this clock
  val reset = Bool // Reset to the ULPI PHY
  val flowDir = Bool().setPartialName("dir") // Direction of data flow between the PHY and SIE
  val next = Bool // Indicator of when the PHY is ready for the next bit
  val stop = Bool // Indicator that transmission of last byte is complete
  val data = XTriState(Bits(8 bits))
  def asMaster(): Unit = {
    out(reset, stop)
    in(clock, flowDir, next)
    master(data)
  }
}
