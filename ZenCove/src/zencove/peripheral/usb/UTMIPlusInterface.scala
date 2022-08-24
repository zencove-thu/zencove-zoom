package zencove.peripheral.usb

import spinal.core._
import spinal.lib._
import zencove.util.XTriState

final case class UTMIPlusRxPayload() extends Bundle {
  val active, error = Bool
}

object USBLineState extends SpinalEnum {
  val SE0, J, K, SE1 = newElement()
}

/** UTMI+ interface. Input UTMI clock not included.
  */
final case class UTMIPlusInterface() extends Bundle with IMasterSlave {
  val reset = Bool
  val suspendn = Bool // suspend, active LOW
  val chrgVbus, dischrgVbus, vbusValid = Bool
  val idPullup, dpPulldown, dmPulldown, idDig = Bool
  val hostDisconnect = Bool
  val opMode = Bits(2 bits)
  val lineState = USBLineState()
  val xcvrSelect = Bits(2 bits)
  val termSelect = Bool
  val sessEnd = Bool
  val tx = Event
  val rx = Flow(UTMIPlusRxPayload())
  val data = XTriState(Bits(8 bits))
  def asMaster(): Unit = {
    out(reset, chrgVbus, dischrgVbus, idPullup, dpPulldown, dmPulldown, opMode)
    out(xcvrSelect, termSelect, suspendn)
    in(idDig, hostDisconnect, lineState, vbusValid, sessEnd)
    master(data, tx)
    slave(rx)
  }
}
