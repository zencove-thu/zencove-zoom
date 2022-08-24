package zencove.blackbox

import spinal.core._
import spinal.lib._
import zencove.util.amba4.axistream._
import zencove.util._
import scala.reflect.io.Directory

/** 除法器IP。用于无符号除法。Non-blocking.
  * https://www.xilinx.com/support/documentation/ip_documentation/div_gen/v5_1/pg151-div-gen.pdf
  *
  * @param dataWidth
  *   被除数和除数的宽度
  * @param detectZero
  *   是否检测除0
  * @note
  *   Vivado的IP会自动把除数和被除数8字节对齐 假设你的输入是33bit，那么输入会到40bit（文档没明确说怎么填充多余的位，估计是高7位填充0） 此时输出的格式是B(0, 7
  *   bits) ## 商(33 bit) ## B(0, 7 bits) ## 余数(33 bit)
  */
class Divider(dataWidth: Int = 32, detectZero: Boolean = false, name: String = "divider")
    extends BlackBox {
  val alignedDataWidth = roundUp(dataWidth, 8).toInt
  setDefinitionName(name)
  noIoPrefix()
  private val inputAxi4Config = Axi4StreamConfig(alignedDataWidth, -1, false, false, false, false)
  private val outputAxi4Config = Axi4StreamConfig(
    2 * alignedDataWidth,
    -1,
    false,
    false,
    false,
    false,
    userWidth = if (detectZero) 1 else -1
  )
  val io = new Bundle {
    val aclk = in Bool ()

    /** 被除数 */
    val dividend = slave(Flow(Axi4Stream(inputAxi4Config))).setName("s_axis_dividend")
    val divisor = slave(Flow(Axi4Stream(inputAxi4Config))).setName("s_axis_divisor")
    val dout = master(Flow(Axi4Stream(outputAxi4Config))).setName("m_axis_dout")
    def divideByZero = dout.user(0)
  }
  Axi4StreamSpecRenamer(io.dividend)
  Axi4StreamSpecRenamer(io.divisor)
  Axi4StreamSpecRenamer(io.dout)
  mapClockDomain(clock = io.aclk)
}

object Divider {
  def main(args: Array[String]) {
    val spinalConfig = new SpinalConfig {
      override val defaultConfigForClockDomains: ClockDomainConfig =
        ClockDomainConfig(resetKind = SYNC)
      override val targetDirectory: String = "generated_verilog/ZenCove"
      override val headerWithDate: Boolean = true
    }
    val dir = Directory(spinalConfig.targetDirectory)
    if (!dir.exists) dir.jfile.mkdirs()

    val rtl = () =>
      new Component {
        setDefinitionName("DividerTop")
        val divider = new Divider
        divider.io.divisor.setIdle()
        divider.io.dividend.setIdle()
      }
    spinalConfig.generateVerilog(rtl()).printUnused()
  }
}
