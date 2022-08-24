package zencove.sim

import spinal.core._
import spinal.core.sim._
import spinal.sim._
import zencove.model._
import zencove.util._
import scala.util.Random

object MultiPortFIFOVecSim {
  def main(args: Array[String]) {
    SpinalSimConfig().withWave.compile(new MultiPortFIFOVec(Bits(32 bits), 16, 4, 4)).doSim { dut =>
      dut.clockDomain.forkStimulus(2)
      dut.clockDomain.waitRisingEdge()
      dut.clockDomain.assertReset()
      dut.clockDomain.waitRisingEdge(2)
      dut.clockDomain.deassertReset()

      for (t <- 1 to 32) {
        for (i <- 0 until dut.io.push.size) {
          dut.io.push(i).valid.randomize()
          dut.io.push(i).payload.randomize()
          dut.io.pop(i).ready.randomize()
        }
        dut.clockDomain.waitRisingEdge()
      }
    }
  }
}
