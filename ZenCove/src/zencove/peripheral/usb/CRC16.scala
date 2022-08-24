package zencove.peripheral.usb

import spinal.core._
import spinal.lib._

/** Compute CRC16.
  */
class CRC16 extends Component {
  val io = new Bundle {
    val crcIn = in(Bits(16 bits))
    val data = in(Bits(8 bits))
    val crcOut = out(Bits(16 bits))
  }
  io.crcOut(15) := io.data(0) ^ io.data(1) ^ io.data(2) ^ io.data(3) ^ io.data(4) ^
    io.data(5) ^ io.data(6) ^ io.data(7) ^ io.crcIn(7) ^ io.crcIn(6) ^
    io.crcIn(5) ^ io.crcIn(4) ^ io.crcIn(3) ^ io.crcIn(2) ^
    io.crcIn(1) ^ io.crcIn(0)
  io.crcOut(14) := io.data(0) ^ io.data(1) ^ io.data(2) ^ io.data(3) ^ io.data(4) ^ io.data(5) ^
    io.data(6) ^ io.crcIn(6) ^ io.crcIn(5) ^ io.crcIn(4) ^
    io.crcIn(3) ^ io.crcIn(2) ^ io.crcIn(1) ^ io.crcIn(0)
  io.crcOut(13) := io.data(6) ^ io.data(7) ^ io.crcIn(7) ^ io.crcIn(6)
  io.crcOut(12) := io.data(5) ^ io.data(6) ^ io.crcIn(6) ^ io.crcIn(5)
  io.crcOut(11) := io.data(4) ^ io.data(5) ^ io.crcIn(5) ^ io.crcIn(4)
  io.crcOut(10) := io.data(3) ^ io.data(4) ^ io.crcIn(4) ^ io.crcIn(3)
  io.crcOut(9) := io.data(2) ^ io.data(3) ^ io.crcIn(3) ^ io.crcIn(2)
  io.crcOut(8) := io.data(1) ^ io.data(2) ^ io.crcIn(2) ^ io.crcIn(1)
  io.crcOut(7) := io.data(0) ^ io.data(1) ^ io.crcIn(15) ^ io.crcIn(1) ^ io.crcIn(0)
  io.crcOut(6) := io.data(0) ^ io.crcIn(14) ^ io.crcIn(0)
  io.crcOut(5) := io.crcIn(13)
  io.crcOut(4) := io.crcIn(12)
  io.crcOut(3) := io.crcIn(11)
  io.crcOut(2) := io.crcIn(10)
  io.crcOut(1) := io.crcIn(9)
  io.crcOut(0) := io.data(0) ^ io.data(1) ^ io.data(2) ^ io.data(3) ^ io.data(4) ^ io.data(5) ^
    io.data(6) ^ io.data(7) ^ io.crcIn(8) ^ io.crcIn(7) ^ io.crcIn(6) ^
    io.crcIn(5) ^ io.crcIn(4) ^ io.crcIn(3) ^ io.crcIn(2) ^
    io.crcIn(1) ^ io.crcIn(0)
}
