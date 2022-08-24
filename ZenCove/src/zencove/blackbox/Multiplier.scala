package zencove.blackbox

import spinal.core._
import spinal.core.sim._
import spinal.sim._
import zencove.blackbox.sim._

/** 乘法器IP。
  * https://www.xilinx.com/support/documentation/ip_documentation/mult_gen/v12_0/pg108-mult-gen.pdf
  * 将其用作无符号乘法，做绝对值乘然后再取符号。
  *
  * @param dataWidth
  *   乘数宽度
  * @param name
  *   definition name
  */
class Multiplier(dataWidth: Int = 32, name: String = "multiplier") extends SimulatedBlackBox {
  setDefinitionName(name)
  noIoPrefix()
  val io = new Bundle {
    val CLK = in Bool ()
    val A = in UInt (dataWidth bits)
    val B = in UInt (dataWidth bits)
    val P = out UInt (dataWidth * 2 bits)
  }
  mapClockDomain(clock = io.CLK)

  override def createSimJob(): Job = {
    val pulled = new Area {
      val P = pullFromOutside(io.P)
    }
    io.A.simPublic()
    io.B.simPublic()
    Pipeline(6)
      .whenIdle {
        // pulled.P.randomize()
        pulled.P #= 0
      }
      .everyTick { schedule =>
        // 无符号乘法
        val result = io.A.toBigInt * io.B.toBigInt
        schedule { pulled.P #= result }
      }
      .toJob
  }
}
