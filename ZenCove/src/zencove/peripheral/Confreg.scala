package zencove.peripheral

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.amba4.axi._
import zencove.util._

object ConfregRegSpace {
  val CR0_ADDR = 0x8000
  val TIMER_ADDR = 0xe000
  val LED_ADDR = 0xf000
  val LED_RG0_ADDR = 0xf004
  val LED_RG1_ADDR = 0xf008
  val NUM_ADDR = 0xf010
  val SWITCH_ADDR = 0xf020
  val BTN_KEY_ADDR = 0xf024
  val BTN_STEP_ADDR = 0xf028
  val SW_INTER_ADDR = 0xf02c
  val LED_MAT_ADDR = 0xf030
  // 0xf034 is high 32 bits
}

/** Configuration registers to control GPIO.
  */
class Confreg extends Component {
  val ctrlRegs = new CsrAddressMapping {
    override val askWrite: Bool = Bool
    override val askRead: Bool = Bool
    override val doWrite: Bool = CombInit(askWrite)
    override val doRead: Bool = CombInit(askRead)
    override val readDataInit: Bits = BWord(0)
    override val readData: Bits = CombInit(readDataInit)
    override val writeData: Bits = BWord()
    override val readToWriteData: Bits = CombInit(readData)
    override val readAddress: UInt = UInt(16 bits)
    override val writeAddress: UInt = UInt(16 bits)
    Component.current.afterElaboration {
      genRead()
      genWrite()
      genOverride()
      genAlways()
    }
  }

  val axiConfig = Axi4Config(32, 32, 6, useQos = false, useRegion = false)
  val io = new Bundle {
    val aclk, aresetn = in(Bool())
    val timer_clk = in(Bool()) // 100MHz固定时钟
    val bus = slave(Axi4(axiConfig))
    // 单色LED
    val led = out(Bits(16 bits))
    // 双色LED
    val led_rg0 = out(Bits(2 bits))
    val led_rg1 = out(Bits(2 bits))
    // 7段数码管
    val num_csn = out(Bits(8 bits)) // 片选
    val num_seg = out(Bits(8 bits)) // {DP,A..G}段
    // 拨码开关
    val switch = in(Bits(8 bits))
    // 按键矩阵
    val btn_key_col = out(Bits(4 bits))
    val btn_key_row = in(Bits(4 bits))
    // 按钮，无防抖
    val btn_step = in(Bits(2 bits))
    // 8*8 LED点阵
    val led_mat_row = out(Bits(8 bits))
    val led_mat_col = out(Bits(8 bits))
    // user-defined contents
    val user_cr0 = out(Bits(32 bits))
    val user_cr1 = out(Bits(32 bits))
  }

  // naming
  setDefinitionName("confreg")
  noIoPrefix()
  Axi4SpecRenamer(io.bus.setPartialName(""))

  import ConfregRegSpace._

  val NUM_CR = 8

  val timerClkWriteTimerBegin = Bool
  val timerClkTimer = UInt(32 bits)

  val defaultDomain = new ClockingArea(
    ClockDomain(
      io.aclk,
      io.aresetn,
      config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
    )
  ) {
    //--------------------------{axi interface}begin-------------------------//
    val busy = RegInit(False)
    val isR = RegInit(False)
    val arEnter = io.bus.ar.fire
    val rRetire = io.bus.r.fire && io.bus.r.last
    val awEnter = io.bus.aw.fire
    val wEnter = io.bus.w.fire && io.bus.w.last
    val bRetire = io.bus.b.fire
    io.bus.ar.ready := ~busy & (!isR | !io.bus.aw.valid)
    io.bus.aw.ready := ~busy & (isR | !io.bus.ar.valid)
    when(arEnter | awEnter) {
      busy := True
    } elsewhen (rRetire | bRetire) {
      busy := False
    }
    val regId = io.bus.ar.id.clone().setAsReg().init(0)
    val regAddr = io.bus.ar.addr.clone().setAsReg().init(0)
    val regLen = io.bus.ar.len.clone().setAsReg().init(0)
    val regSize = io.bus.ar.size.clone().setAsReg().init(0)
    when(awEnter) {
      isR := False
      regId := io.bus.aw.id
      regAddr := io.bus.aw.addr
      regLen := io.bus.aw.len
      regSize := io.bus.aw.size
    }
    when(arEnter) {
      isR := True
      regId := io.bus.ar.id
      regAddr := io.bus.ar.addr
      regLen := io.bus.ar.len
      regSize := io.bus.ar.size
    }
    val regWReady = RegInit(False) clearWhen (wEnter && io.bus.w.last) setWhen (awEnter)
    io.bus.w.ready := regWReady

    // read data has one cycle delay
    ctrlRegs.readAddress := regAddr(15 downto 0)
    ctrlRegs.askRead := busy && isR && !rRetire
    val regRData = RegNextWhen(ctrlRegs.readData, ctrlRegs.doRead, B(0, 32 bits))
    val regRLast = RegInit(False) setWhen (ctrlRegs.doRead)
    val regRValid = RegInit(False) clearWhen (rRetire) setWhen (ctrlRegs.doRead)
    io.bus.r.data := regRData
    io.bus.r.valid := regRValid
    io.bus.r.last := regRLast

    //conf write, only support a word write
    ctrlRegs.askWrite := wEnter
    ctrlRegs.writeAddress := regAddr(15 downto 0)
    ctrlRegs.writeData := io.bus.w.data
    val regBValid = RegInit(False) clearWhen (bRetire) setWhen (wEnter)
    io.bus.b.valid := regBValid
    io.bus.r.id := regId
    io.bus.b.id := regId
    io.bus.b.resp := Axi4.resp.OKAY
    io.bus.r.resp := Axi4.resp.OKAY
    //---------------------------{axi interface}end--------------------------//

    //-------------------------{confreg register}begin-----------------------//
    val cr = Vec(RegInit(B(0, 32 bits)), NUM_CR)
    for (i <- 0 until NUM_CR) {
      ctrlRegs.rw(CR0_ADDR + i * 4, cr(i))
    }
    io.user_cr0 := cr(0)
    io.user_cr1 := cr(1)
    //-------------------------{confreg register}end-----------------------//

    //-------------------------------{timer}begin----------------------------//
    val writeTimer = ctrlRegs.isWriting(TIMER_ADDR)
    val writeTimerEnd = BufferCC(timerClkWriteTimerBegin)
    val writeTimerBegin = RegInit(False) clearWhen (writeTimerEnd) setWhen (writeTimer)
    val regWData = RegNextWhen(ctrlRegs.writeData, writeTimer)
    val timer = BufferCC(timerClkTimer)
    // write is synchronized to timer_clk
    ctrlRegs.r(TIMER_ADDR, timer)
    //-------------------------------{timer}end----------------------------//

    //--------------------------------{led}begin-----------------------------//
    val switchLed = Bits(16 bits)
    for (i <- 0 until 8) {
      switchLed(i * 2) := io.switch(i)
      switchLed(i * 2 + 1) := io.switch(i)
    }
    val ledData = RegInit(switchLed)
    ctrlRegs.rw(LED_ADDR, ledData)
    io.led := ledData
    //--------------------------------{led}end-----------------------------//

    //-------------------------------{switch}begin---------------------------//
    ctrlRegs.r(SWITCH_ADDR, io.switch)
    val swInter = Bits(16 bits)
    for (i <- 0 until 8) {
      swInter(i * 2) := False
      swInter(i * 2 + 1) := io.switch(i)
    }
    ctrlRegs.r(SW_INTER_ADDR, swInter)
    //-------------------------------{switch}end---------------------------//

    //------------------------------{btn key}begin---------------------------//
    val btnKeyTmp = B(0, 16 bits)
    val regBtnKey = RegInit(B(0, 16 bits))
    ctrlRegs.r(BTN_KEY_ADDR, regBtnKey)
    val btnKeyRowInactive = io.btn_key_row.andR
    //eliminate jitter
    val keyFlag = RegInit(False)
    val keyCount = Counter(20 bits)
    keyCount.increment() // free run
    when(!keyFlag) { keyCount.clear() }
    val keySample = keyCount.value.msb
    val keyStart, keyEnd = False
    keyFlag setWhen (keyStart || keyEnd)
    val stateCount = CounterFreeRun(17)
    val stateSample = stateCount.value.msb
    keyFlag clearWhen (keySample && stateSample)
    io.btn_key_col := 0
    val btnKeyFsm = new StateMachine {
      val COL0, COL1, COL2, COL3, FINISH = new State
      setEntry(state = stateBoot)
      disableAutoStart()
      setTransitionCondition(stateSample)

      stateBoot.whenIsActive {
        regBtnKey := 0
        keyStart := !btnKeyRowInactive
        when(keySample && !btnKeyRowInactive) {
          goto(COL0)
        }
      }
      COL0.whenIsActive {
        io.btn_key_col := B"4'b1110"
        for (i <- 0 until 4) when(!io.btn_key_row(i)) { btnKeyTmp(i * 4) := True }
        when(btnKeyRowInactive) { goto(COL1) } otherwise { goto(FINISH) }
      }
      COL1.whenIsActive {
        io.btn_key_col := B"4'b1101"
        for (i <- 0 until 4) when(!io.btn_key_row(i)) { btnKeyTmp(i * 4 + 1) := True }
        when(btnKeyRowInactive) { goto(COL2) } otherwise { goto(FINISH) }
      }
      COL2.whenIsActive {
        io.btn_key_col := B"4'b1011"
        for (i <- 0 until 4) when(!io.btn_key_row(i)) { btnKeyTmp(i * 4 + 2) := True }
        when(btnKeyRowInactive) { goto(COL3) } otherwise { goto(FINISH) }
      }
      COL3.whenIsActive {
        io.btn_key_col := B"4'b0111"
        for (i <- 0 until 4) when(!io.btn_key_row(i)) { btnKeyTmp(i * 4 + 3) := True }
        when(btnKeyRowInactive) { goto(stateBoot) } otherwise { goto(FINISH) }
      }
      FINISH.whenIsActive {
        keyEnd := btnKeyRowInactive
        when(keySample && btnKeyRowInactive) {
          goto(stateBoot)
        }
      }
      FINISH.onEntry {
        regBtnKey := btnKeyTmp
      }
    }
    //------------------------------{btn key}end---------------------------//

    //-----------------------------{btn step}begin---------------------------//
    class BtnStepArea(idx: Int) extends Area {
      val regBtnStep = RegInit(True) // active LOW
      ctrlRegs.r(BTN_STEP_ADDR, 1 - idx, !regBtnStep) // active HIGH
      val stepCount = Counter(20 bits)
      stepCount.increment()
      val stepSample = stepCount.value.msb
      val stepFlag = RegInit(False) setWhen (io.btn_step(idx) ^ regBtnStep) clearWhen (stepSample)
      when(!stepFlag) { stepCount.clear() }
      when(stepSample) { regBtnStep := io.btn_step(idx) }
    }
    val step0 = new BtnStepArea(0)
    val step1 = new BtnStepArea(1)
    //-----------------------------{btn step}end---------------------------//

    //-------------------------------{led rg}begin---------------------------//
    val ledRg0, ledRg1 = RegInit(B(0, 2 bits))
    ctrlRegs.rw(LED_RG0_ADDR, ledRg0)
    ctrlRegs.rw(LED_RG1_ADDR, ledRg1)
    io.led_rg0 := ledRg0
    io.led_rg1 := ledRg1
    //--------------------------------{led rg}end----------------------------//

    //---------------------------{digital number}begin-----------------------//
    val numData = RegInit(U(0, 32 bits))
    ctrlRegs.rw(NUM_ADDR, numData)
    val count = Counter(20 bits)
    count.increment() // free run
    val scanData = RegInit(U(0, 4 bits))
    val numCSn = RegInit(Bits(8 bits).setAll())
    for (i <- 0 to 7) when(count(19 downto 17) === 7 - i) {
      scanData := numData(i * 4, 4 bits)
      numCSn := (1 << 8) - 1 - (1 << i)
    }
    val numSeg = RegInit(B(0, 7 bits))
    numSeg := scanData.mux(
      0x0 -> B"7'b1111110",
      0x1 -> B"7'b0110000",
      0x2 -> B"7'b1101101",
      0x3 -> B"7'b1111001",
      0x4 -> B"7'b0110011",
      0x5 -> B"7'b1011011",
      0x6 -> B"7'b1011111",
      0x7 -> B"7'b1110000",
      0x8 -> B"7'b1111111",
      0x9 -> B"7'b1111011",
      0xa -> B"7'b1110111",
      0xb -> B"7'b0011111",
      0xc -> B"7'b1001110",
      0xd -> B"7'b0111101",
      0xe -> B"7'b1001111",
      0xf -> B"7'b1000111"
    )
    io.num_csn := numCSn
    io.num_seg(6 downto 0) := numSeg
    io.num_seg(7) := False // DP小数点，恒0
    //---------------------------{digital number}end-----------------------//

    //---------------------------{led matrix}begin-----------------------//
    // Use the same scan rate as digital number
    val ledMatData = RegInit(B(0, 64 bits))
    ctrlRegs.rw(LED_MAT_ADDR, ledMatData(0, 32 bits))
    ctrlRegs.rw(LED_MAT_ADDR + 4, ledMatData(32, 32 bits))
    val ledMatRow = RegInit(B(0, 8 bits)) // active HIGH selects row
    val ledMatCol = Reg(Bits(8 bits)) // active LOW
    for (i <- 0 to 7) when(count(19 downto 17) === i) {
      ledMatCol := ~ledMatData(i * 8, 8 bits)
      ledMatRow := (1 << i)
    }
    io.led_mat_row := ledMatRow
    io.led_mat_col := ledMatCol
    //---------------------------{led matrix}end-----------------------//
  }

  val timerDomain = new ClockingArea(
    ClockDomain(
      io.timer_clk,
      io.aresetn,
      config = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = LOW)
    )
  ) {
    val writeTimerBegin = BufferCC(defaultDomain.writeTimerBegin)
    timerClkWriteTimerBegin := writeTimerBegin
    val regWData = BufferCC(defaultDomain.regWData)
    val timer = RegInit(U(0, 32 bits))
    timerClkTimer := timer
    when(writeTimerBegin.rise()) {
      timer := regWData.asUInt
    } otherwise {
      timer := timer + 1
    }
  }
}

object Confreg {
  def main(args: Array[String]): Unit = {
    SpinalConfig(
      targetDirectory = "generated_verilog"
    ).generateVerilog(new Confreg)
  }
}
