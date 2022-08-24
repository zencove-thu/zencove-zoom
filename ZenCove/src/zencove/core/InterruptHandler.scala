package zencove.core

import zencove.builder.Plugin
import zencove.enum._
import spinal.core._
import spinal.lib.Flow
import zencove.InterruptConfig

class InterruptHandler(config: InterruptConfig) extends Plugin[CPU] with IntStatusProvider {
  override val intVecOffset = Flow(UInt(12 bits))
  val extInt = Bits(6 bits) default 0
  val timerInt = Bool default False
  val softInt = RegInit(B(0, 2 bits))
  val IV = RegInit(False)
  val IE = RegInit(False)
  val IP = Bits(8 bits)
  val IM = Reg(Bits(8 bits))
  // means timer interrupt connected to interrupt source 4(IP 4), r, not modifiable
  val IPTI = U(config.timerIntIP, 3 bits)
  // can take interrupt
  val canTakeInt = Bool
  override def setup(pipeline: CPU): Unit = {
    import zencove.MIPS32.CP0
    val cp0 = pipeline.service[CP0Regs]

    cp0.r(CP0.Cause, 30 -> timerInt) // TI
    cp0.rw(CP0.Cause, 23 -> IV)
    cp0.r(CP0.Cause, 8 -> IP)
    cp0.w(CP0.Cause, 8 -> softInt)

    cp0.rw(CP0.Status, 8 -> IM)
    cp0.rw(CP0.Status, 0 -> IE)

    cp0.r(CP0.IntCtl, 29 -> IPTI)
  }
  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    val excHandler = pipeline.service[ExceptionHandler]
    val configRegs = pipeline.service[ConfigRegisters]
    // IP bits
    IP(1 downto 0) := softInt
    for (i <- 2 until 8) {
      if (config.withTimerInt && i == config.timerIntIP) {
        IP(i) := timerInt || RegNext(extInt(i - 2), init = False)
      } else {
        IP(i) := RegNext(extInt(i - 2), init = False)
      }
    }

    canTakeInt := IE && excHandler.normalLevel
    // only support compatibility mode
    intVecOffset.valid := canTakeInt && (IM & IP).orR
    intVecOffset.payload := Mux(IV, U(0x200), U(0x180)).resized
  }
}
