package zencove.util

import spinal.core._

class Seg7 extends Component {
  val io = new Bundle {
    val num = in UInt (4 bits)
    val seg = out Bits (7 bits)
  }

  io.seg := io.num.mux(
    0 -> B"7'b0111111",
    1 -> B"7'b0001001",
    2 -> B"7'b1011110",
    3 -> B"7'b1011011",
    4 -> B"7'b1101001",
    5 -> B"7'b1110011",
    6 -> B"7'b1110111",
    7 -> B"7'b0011001",
    8 -> B"7'b1111111",
    9 -> B"7'b1111001",
    0xa -> B"7'b1111101",
    0xb -> B"7'b1100111",
    0xc -> B"7'b0110110",
    0xd -> B"7'b1001111",
    0xe -> B"7'b1110110",
    0xf -> B"7'b1110100"
  )

}
