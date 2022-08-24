package zencove.peripheral

import spinal.core._
import spinal.lib._
import zencove.util.amba4.axistream._

/** Modify video stream data, controlled by input register.
  */
class VideoStreamControl extends Component {
  setDefinitionName("stream_ctl")
  noIoPrefix()
  val axiConfig = Axi4StreamConfig(24, idWidth = 1, destWidth = 1, userWidth = 1)
  val io = new Bundle {
    val ctl_reg1 = in(Bits(32 bits)) // can be asynchronous to current clock
    val videoIn = slave(Stream(Axi4Stream(axiConfig))).setName("s_axis_video")
    val videoOut = master(Stream(Axi4Stream(axiConfig))).setName("m_axis_video")
  }
  Axi4StreamSpecRenamer(io.videoIn)
  Axi4StreamSpecRenamer(io.videoOut)
  val ctrlReg1 = BufferCC(io.ctl_reg1)
  val enConstant = ctrlReg1(31)
  val constantVal = ctrlReg1(23 downto 0)
  io.videoOut << io.videoIn
  io.videoOut.data.allowOverride := Mux(enConstant, constantVal, io.videoIn.data ^ constantVal)
}

object VideoStreamControl {
  def main(args: Array[String]): Unit = {
    SpinalConfig(
      defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW),
      targetDirectory = "generated_verilog"
    ).generateVerilog(new VideoStreamControl)
  }
}
