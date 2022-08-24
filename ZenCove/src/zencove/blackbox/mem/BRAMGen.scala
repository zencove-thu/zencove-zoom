package zencove.blackbox.mem

import spinal.core._

class BRAMGen(dataWidth: Int, addressWidth: Int) extends BlackBox {
  noIoPrefix()

  val io = new Bundle {
    val write = new Bundle {
      val addr = in(UInt(addressWidth bits))
      val clk = in(Bool)
      val din = in(Bits(dataWidth bits))
      val we = in(Bool)
    }
    val read = new Bundle {
      val addr = in(UInt(addressWidth bits))
      val clk = in(Bool)
      val dout = out(Bits(dataWidth bits))
      val en = in(Bool)
    }
  }

  addPrePopTask(() => {
    io.flatten.foreach(bt => {
      if (bt.getName().contains("write"))
        bt.setName(bt.getName().replace("write_", "") + "a")
      if (bt.getName().contains("read"))
        bt.setName(bt.getName().replace("read_", "") + "b")
    })
  })

  mapClockDomain(clock = io.write.clk)
  mapClockDomain(clock = io.read.clk)
}
