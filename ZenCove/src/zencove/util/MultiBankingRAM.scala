package zencove.util

import spinal.core._
import spinal.lib._
import zencove.blackbox.mem.SDPRAM

final case class RAMReadPort[T <: Data](wordType: HardType[T], addressWidth: Int)
    extends Bundle
    with IMasterSlave {
  val cmd = Stream(UInt(addressWidth bits))
  val rsp = wordType()

  override def asMaster(): Unit = {
    master(cmd)
    in(rsp)
  }
}
final case class RAMWriteCmd[T <: Data](wordType: HardType[T], addressWidth: Int) extends Bundle {
  val address = UInt(addressWidth bits)
  val data = wordType()
}

/** 多端口互相独立，只要不发生bank conflict就可以同时读写。
  *
  * 使用SDP block ram w/ primitive register实现，2周期延迟。
  *
  * @param wordType
  * @param wordCount
  * @param bankCount
  * @param readPortCount
  * @param writePortCount
  */
class MultiBankingRAM[T <: Data](
    wordType: HardType[T],
    wordCount: Int,
    bankCount: Int,
    readPortCount: Int,
    writePortCount: Int
) extends Component {
  assert(isPow2(bankCount), "bank count should be power of 2")
  private val bankWidth = log2Up(bankCount)
  val wordsPerBank = wordCount / bankCount
  val addressWidth = log2Up(wordCount)
  val rams = Seq.fill(bankCount)(new SDPRAM(wordType, wordsPerBank, true))
  rams.zipWithIndex.foreach { case (ram, idx) =>
    ram.setWeakName("ram" + idx)
    OwnableRef.proposal(ram, this)
  }
  val io = new Bundle {
    val readPorts = Vec(slave(RAMReadPort(wordType, addressWidth)), readPortCount)
    val writePorts = Vec(slave(Stream(RAMWriteCmd(wordType, addressWidth))), writePortCount)
  }
  val rPorts = Vec(rams.map(_.io.read))
  val wPorts = Vec(rams.map(_.io.write))
  // 读rsp选择
  io.readPorts.map { p =>
    new Composite(p) {
      p.cmd.setBlocked()
      val regAddr = Delay(p.cmd.payload(0, bankWidth bits), 2, p.cmd.valid)
      // 将addr延迟两个周期，选择正确的rsp
      p.rsp := rPorts(regAddr).rsp
    }
  }
  // 读address选择
  for (i <- 0 until bankCount; mem = rams(i); port = rPorts(i)) {
    val valids = io.readPorts.map { p =>
      p.cmd.valid && p.cmd.payload(0, bankWidth bits) === i
    }
    val selOH = OHMasking.first(valids)
    val selIdx = OHToUInt(selOH)
    val ioPort = io.readPorts(selIdx)
    port.cmd.valid := valids.orR
    port.cmd.payload := ioPort.cmd.payload((addressWidth - 1) downto bankWidth)
    // 每个bank选择一个port来serve，回复ready
    ioPort.cmd.ready := True
  }
  // 写address选择
  io.writePorts.foreach(_.setBlocked())
  for (i <- 0 until bankCount; mem = rams(i); port = wPorts(i)) {
    val valids = io.writePorts.map { p =>
      p.valid && p.payload.address(0, bankWidth bits) === i
    }
    val selOH = OHMasking.first(valids)
    val selIdx = OHToUInt(selOH)
    val ioPort = io.writePorts(selIdx)
    port.valid := valids.orR
    port.payload.address := ioPort.payload.address((addressWidth - 1) downto bankWidth)
    port.payload.data := ioPort.payload.data
    // 每个bank选择一个port来serve，回复ready
    ioPort.ready := True
  }
}
