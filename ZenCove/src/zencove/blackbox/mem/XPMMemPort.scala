package zencove.blackbox.mem

import spinal.core._

final case class XPMMemPort(wWidth: Int, rWidth: Int, addrWidth: Int, weWidth: Int) extends Bundle {
  val clk = in(Bool)
  val rst = in(Bool)
  val en = in(Bool)
  val we = in(Bits(weWidth bits))
  val regce = in(Bool)
  val addr = in(UInt(addrWidth bits))
  val dout = out(Bits(rWidth bits))
  val sbiterr = out(Bool)
  val dbiterr = out(Bool)
  val din = in(Bits(wWidth bits))
  val injectsbiterr = in(Bool)
  val injectdbiterr = in(Bool)
}
