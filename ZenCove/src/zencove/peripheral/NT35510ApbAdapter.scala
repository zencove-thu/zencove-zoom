package zencove.peripheral

import spinal.core._
import spinal.lib.bus.amba3.apb._
import spinal.lib._
import spinal.lib.fsm._

/** NT35510 LCD controller with APB bus.
  */
class NT35510ApbAdapter extends Component {
  noIoPrefix()
  val apbConfig = Apb3Config(32, 32)
  val io = new Bundle {
    val apb = slave(Apb3(apbConfig))
    val lcd = master(LCDInterface())
  }

  val rdCycle = 50
  val wrCycle = 5
  val rsCycle = 3
  val apbInstAddr = 0
  val apbDataAddr = 4

  val regCs = RegInit(True)
  val regWr = RegInit(True)
  val regRs = RegInit(False)
  val regRd = RegInit(True)
  val regWData = Reg(Bits(16 bits))
  val regTriState = RegInit(Bits(16 bits).setAll())
  val cycleCount = RegInit(U(0, 6 bits))
  val targetCount = RegInit(U(0, 6 bits))
  io.lcd.csel := regCs
  io.lcd.rd := regRd
  io.lcd.rs := regRs
  io.lcd.wr := regWr
  io.lcd.nrst := clockDomain.readResetWire
  io.lcd.data.t := regTriState
  io.lcd.data.o := regWData

  io.apb.PSLVERROR := False
  io.apb.PREADY := False

  val enable = io.apb.PENABLE && io.apb.PSEL(0)

  val fsm = new StateMachine {
    disableAutoStart()
    setEntry(stateBoot)
    val setupRs, access, ready, stall = new State
    stateBoot.whenIsActive {
      when(enable) {
        regRs := io.apb.PADDR(2 downto 0) =/= apbInstAddr
        cycleCount := 0
        goto(setupRs)
      }
    }
    setupRs.whenIsActive {
      cycleCount := cycleCount + 1
      when(cycleCount === rsCycle) {
        when(io.apb.PWRITE) {
          regCs := False
          regTriState.clearAll()
          regWData := io.apb.PWDATA(15 downto 0)
          regWr := False
          targetCount := wrCycle
        } otherwise {
          regRd := False
          targetCount := rdCycle
        }
        cycleCount := 0
        goto(access)
      }
    }
    access.whenIsActive {
      cycleCount := cycleCount + 1
      when(cycleCount === targetCount) {
        when(!io.apb.PWRITE) {
          io.apb.PRDATA.setAsReg() := B(0, 16 bits) ## io.lcd.data.i
        }
        regWr := True
        regRd := True
        goto(ready)
      }
    }
    ready.whenIsActive {
      io.apb.PREADY := True
      when(!enable) {
        cycleCount := 0
        goto(stall)
      }
    }
    stall.whenIsActive {
      cycleCount := cycleCount + 1
      when(cycleCount === targetCount) {
        regCs := True
        regTriState.setAll()
        goto(stateBoot)
      }
    }
  }
}

object NT35510ApbAdapter {
  def main(args: Array[String]): Unit = {
    SpinalConfig(
      defaultConfigForClockDomains = ClockDomainConfig(resetKind = ASYNC, resetActiveLevel = LOW),
      targetDirectory = "generated_verilog"
    ).generateVerilog(new NT35510ApbAdapter)
  }
}
