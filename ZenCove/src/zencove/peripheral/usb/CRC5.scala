package zencove.peripheral.usb

import spinal.core._
import spinal.lib._

/** Compute CRC5.
  */
class CRC5 extends Component {
  val io = new Bundle {
    val crcIn = in(Bits(5 bits))
    val data = in(Bits(11 bits))
    val crcOut = out(Bits(5 bits))
  }
  io.crcOut(0) := io.data(10) ^ io.data(9) ^ io.data(6) ^ io.data(5) ^ io.data(3) ^ io.data(0) ^
    io.crcIn(0) ^ io.crcIn(3) ^ io.crcIn(4)

  io.crcOut(1) := io.data(10) ^ io.data(7) ^ io.data(6) ^ io.data(4) ^ io.data(1) ^
    io.crcIn(0) ^ io.crcIn(1) ^ io.crcIn(4)

  io.crcOut(2) := io.data(10) ^ io.data(9) ^ io.data(8) ^ io.data(7) ^ io.data(6) ^ io.data(3) ^ io
    .data(2) ^ io.data(0) ^
    io.crcIn(0) ^ io.crcIn(1) ^ io.crcIn(2) ^ io.crcIn(3) ^ io.crcIn(4)

  io.crcOut(3) := io.data(10) ^ io.data(9) ^ io.data(8) ^ io.data(7) ^ io.data(4) ^ io.data(3) ^ io
    .data(1) ^
    io.crcIn(1) ^ io.crcIn(2) ^ io.crcIn(3) ^ io.crcIn(4)

  io.crcOut(4) := io.data(10) ^ io.data(9) ^ io.data(8) ^ io.data(5) ^ io.data(4) ^ io.data(2) ^
    io.crcIn(2) ^ io.crcIn(3) ^ io.crcIn(4)
}
