package zencove.peripheral.usb

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.amba4.axilite._
import zencove.util._
import zencove.blackbox.mem.FwftFifo
import spinal.lib.misc.Timer

object USBRegSpace {
  val USB_CTRL = 0
  val USB_STATUS = 4
  val USB_IRQ_ACK = 8
  val USB_IRQ_STS = 0x0c
  val USB_IRQ_MASK = 0x10
  val USB_XFER_DATA = 0x14
  val USB_XFER_TOKEN = 0x18
  val USB_RX_STAT = 0x1c
  val USB_RW_DATA = 0x20
  val USB_CTRL2 = 0x24
}

object USBPid {
  val PID_OUT = 0xe1
  val PID_IN = 0x69
  val PID_SOF = 0xa5
  val PID_SETUP = 0x2d

  val PID_DATA0 = 0xc3
  val PID_DATA1 = 0x4b

  val PID_ACK = 0xd2
  val PID_NAK = 0x5a
  val PID_STALL = 0x1e
}

/** USB interrupt types.
  */
final case class USBIrq() extends Bundle {
  val sof, done, err, deviceDetect = Bool
}

/** USB 2.0 Full Speed host controller. UTMI+ interface.
  */
class USBHost extends Component {
  val ctrlRegs = new CsrAddressMapping {
    override val askWrite: Bool = Bool
    override val askRead: Bool = Bool
    override val doWrite: Bool = CombInit(askWrite)
    override val doRead: Bool = CombInit(askRead)
    override val readDataInit: Bits = BWord(0)
    override val readData: Bits = CombInit(readDataInit)
    override val writeData: Bits = BWord()
    override val readToWriteData: Bits = CombInit(readData)
    override val readAddress: UInt = UInt(8 bits)
    override val writeAddress: UInt = UInt(8 bits)
    Component.current.afterElaboration {
      genRead()
      genWrite()
      genOverride()
      genAlways()
    }
  }

  val axiConfig = AxiLite4Config(32, 32)
  val io = new Bundle {
    val cfg = slave(AxiLite4(axiConfig))
    val utmi = master(UTMIPlusInterface())
    val intr = out(Bool)
  }

  // naming
  noIoPrefix()
  AxiLite4SpecRenamer(io.cfg)
  afterElaboration {
    io.utmi.suspendn.setPartialName("suspend_n")
    io.utmi.xcvrSelect.setPartialName("xcvrsel")
    io.utmi.termSelect.setPartialName("termsel")
    io.utmi.hostDisconnect.setPartialName("hostdisc")
    io.utmi.flatten.foreach { bt =>
      bt.setPartialName(bt.getPartialName().toLowerCase())
      bt.setName(bt.getName().replace("_payload_", ""))
      bt.setName(bt.getName().replace("_valid", "valid"))
      bt.setName(bt.getName().replace("_ready", "ready"))
    }
  }

  import USBRegSpace._
  import USBPid._

  val USB_CLK_FREQ = ClockDomain.current.frequency.getValue.toLong
  val SOF_ZERO = 0
  val SOF_INC = 1
  val SOF_THRESHOLD = (USB_CLK_FREQ / 1000) - 1
  val CLKS_PER_BIT = (USB_CLK_FREQ / 12000000) // input clks per FS bit time
  val EOF1_THRESHOLD = (50 * CLKS_PER_BIT) // EOF1 + some margin
  val MAX_XFER_SIZE = 64
  val MAX_XFER_PERIOD =
    ((MAX_XFER_SIZE + 6) * 10 * CLKS_PER_BIT) // Max packet transfer time (+ margin)
  val SOF_GAURD_LOW = (20 * CLKS_PER_BIT)
  val SOF_GAURD_HIGH = SOF_THRESHOLD - EOF1_THRESHOLD - MAX_XFER_PERIOD
  val USB_60M = ClockDomain.current.frequency.getValue.equals(60 MHz)
  val RX_TIMEOUT = if (USB_60M) 511 else 255
  val TX_IFS_V = if (USB_60M) 10 else 7
  val TX_IFS_W = 4

  // unused pins
  io.utmi.idPullup := False
  io.utmi.chrgVbus := False
  io.utmi.dischrgVbus := False
  io.utmi.suspendn := True

  // Tx FIFO (Host -> Device)
  val txFifo = new StreamFifo(Bits(8 bits), 64)
  // val txFifo = new FwftFifo(Bits(8 bits), 64)
  // Rx FIFO (Device -> Host)
  val rxFifo = new StreamFifo(Bits(8 bits), 64)
  // val rxFifo = new FwftFifo(Bits(8 bits), 64)
  val crc5 = new CRC5
  val crc16 = new CRC16

  //-----------------------------------------------------------------
  // Write address / data split
  //-----------------------------------------------------------------
  // Address but no data ready
  val regAwValid = RegInit(False)
  // Data but no data ready
  val regWValid = RegInit(False)
  val wrCmdAccepted = io.cfg.aw.fire || regAwValid
  val wrDataAccepted = io.cfg.w.fire || regWValid
  regAwValid clearWhen (wrDataAccepted) setWhen (io.cfg.aw.fire && !wrDataAccepted)
  regWValid clearWhen (wrCmdAccepted) setWhen (io.cfg.w.fire && !wrCmdAccepted)

  //-----------------------------------------------------------------
  // Capture address (for delayed data)
  //-----------------------------------------------------------------
  val regWrAddr = RegNextWhen(io.cfg.aw.addr(7 downto 0), io.cfg.aw.fire, U(0, 8 bits))
  val wrAddr = Mux(regAwValid, regWrAddr, io.cfg.aw.addr(7 downto 0))

  //-----------------------------------------------------------------
  // Retime write data
  //-----------------------------------------------------------------
  val regWrData = RegNextWhen(io.cfg.w.data, io.cfg.w.fire, BWord(0))

  //-----------------------------------------------------------------
  // Request Logic
  //-----------------------------------------------------------------
  val readEn = io.cfg.ar.fire
  val writeEn = wrCmdAccepted && wrDataAccepted

  //-----------------------------------------------------------------
  // Accept Logic
  //-----------------------------------------------------------------
  io.cfg.ar.ready := !io.cfg.r.valid
  io.cfg.aw.ready := !io.cfg.b.valid && !io.cfg.ar.valid && !regAwValid
  io.cfg.w.ready := !io.cfg.b.valid && !io.cfg.ar.valid && !regWValid

  //-----------------------------------------------------------------
  // Ctrl Regs
  //-----------------------------------------------------------------
  ctrlRegs.writeAddress := wrAddr
  ctrlRegs.askWrite := writeEn
  ctrlRegs.writeData := io.cfg.w.data

  //-----------------------------------------------------------------
  // Register usb_ctrl
  //-----------------------------------------------------------------
  val regUsbCtrlWr = RegNext(False, False)
  ctrlRegs.onWrite(USB_CTRL) { regUsbCtrlWr := True }
  val txFlush = RegNext(False, False)
  txFifo.io.flush := txFlush
  val dmPulldown, dpPulldown, termSelect, enableSof = RegInit(False)
  val xcvrSelect, opMode = RegInit(B(0, 2 bits))
  ctrlRegs.w(USB_CTRL, 8 -> txFlush)
  ctrlRegs.rw(
    USB_CTRL,
    7 -> dmPulldown,
    6 -> dpPulldown,
    5 -> termSelect,
    3 -> xcvrSelect,
    1 -> opMode,
    0 -> enableSof
  )
  io.utmi.opMode := opMode
  io.utmi.xcvrSelect := xcvrSelect
  io.utmi.termSelect := termSelect
  io.utmi.dpPulldown := dpPulldown
  io.utmi.dmPulldown := dmPulldown

  //-----------------------------------------------------------------
  // Register usb_irq_ack
  //-----------------------------------------------------------------
  val irqAck = RegNext(USBIrq().getZero, USBIrq().getZero)
  ctrlRegs.w(USB_IRQ_ACK, 0 -> irqAck)

  //-----------------------------------------------------------------
  // Register usb_irq_mask
  //-----------------------------------------------------------------
  val irqMask = RegInit(USBIrq().getZero)
  ctrlRegs.rw(USB_IRQ_MASK, 0 -> irqMask)

  //-----------------------------------------------------------------
  // Register usb_xfer_data
  //-----------------------------------------------------------------
  val txLen = RegInit(U(0, 16 bits))
  ctrlRegs.rw(USB_XFER_DATA, 0 -> txLen)

  //-----------------------------------------------------------------
  // Register usb_xfer_token
  //-----------------------------------------------------------------
  val tokenStartAckIn = Bool
  val tokenStart = RegInit(False) clearWhen (tokenStartAckIn)
  val tokenIn, tokenAck, tokenPidDatax = RegInit(False)
  val tokenPidBits = RegInit(B(0, 8 bits))
  val tokenDevAddr = RegInit(U(0, 7 bits))
  val tokenEpAddr = RegInit(U(0, 4 bits))
  ctrlRegs.w(USB_XFER_TOKEN, 31 -> tokenStart)
  ctrlRegs.rw(
    USB_XFER_TOKEN,
    30 -> tokenIn,
    29 -> tokenAck,
    28 -> tokenPidDatax,
    16 -> tokenPidBits,
    9 -> tokenDevAddr,
    5 -> tokenEpAddr
  )

  //-----------------------------------------------------------------
  // Register usb_wr_data
  //-----------------------------------------------------------------
  val regRWDataWr = RegNext(False, False)
  ctrlRegs.onWrite(USB_RW_DATA) { regRWDataWr := True }
  txFifo.io.push.valid := regRWDataWr
  txFifo.io.push.payload := regWrData(7 downto 0)

  //-----------------------------------------------------------------
  // Register usb_ctrl2 (jiegec)
  //-----------------------------------------------------------------
  val phyReset = RegInit(False)
  ctrlRegs.w(USB_CTRL2, 0 -> phyReset)
  io.utmi.reset := phyReset

  //-----------------------------------------------------------------
  // RVALID
  //-----------------------------------------------------------------
  val regRValid = RegInit(False) clearWhen (io.cfg.r.ready) setWhen (readEn)
  io.cfg.r.valid := regRValid

  //-----------------------------------------------------------------
  // Retime read response
  //-----------------------------------------------------------------
  val regRData = RegNextWhen(ctrlRegs.readData, io.cfg.r.isFree, BWord(0))
  io.cfg.r.data := regRData
  io.cfg.r.resp := AxiLite4.resp.OKAY

  //-----------------------------------------------------------------
  // BVALID
  //-----------------------------------------------------------------
  val regBValid = RegInit(False) clearWhen (io.cfg.b.ready) setWhen (writeEn)
  io.cfg.b.valid := regBValid
  io.cfg.b.resp := AxiLite4.resp.OKAY
  val rwDataRd = ctrlRegs.isReading(USB_RW_DATA)

  //-----------------------------------------------------------------
  // Assignments
  //-----------------------------------------------------------------
  val sieIdle = False
  val sofTime = RegInit(U(SOF_ZERO, 16 bits))
  val sofTransfer = RegInit(False)
  val sendSof = sofTime === SOF_THRESHOLD && enableSof && sieIdle
  val sofValue = RegInit(U(0, 11 bits))
  val sofIrq = RegInit(False)
  val sofGuardBand = sofTime <= SOF_GAURD_LOW || sofTime >= SOF_GAURD_HIGH
  val clearToSend = (!sofGuardBand || !enableSof) && sieIdle
  val tokenPid = Mux[Bits](sofTransfer, PID_SOF, tokenPidBits)
  val tokenDev = Mux(sofTransfer, sofValue(6 downto 0), tokenDevAddr).reversed
  val tokenEp = Mux(sofTransfer, sofValue(10 downto 7), tokenEpAddr).reversed

  //-----------------------------------------------------------------
  // Transfer start ack
  //-----------------------------------------------------------------
  val startAck = RegNext(False, False)

  //-----------------------------------------------------------------
  // Control logic
  //-----------------------------------------------------------------
  val transferAck = startAck
  val fifoFlush, transferStart = RegInit(False)
  val transferReqAck, inTransfer, respExpected = RegInit(False)
  tokenStartAckIn := transferReqAck
  rxFifo.io.flush := fifoFlush
  when(transferStart) {
    // Transfer in progress?
    when(transferAck) {
      // Transfer accepted
      transferStart := False
    }
    fifoFlush := False
    transferReqAck := False
  } elsewhen (sendSof) {
    // Time to send another SOF token?
    inTransfer := False
    respExpected := False
    transferStart := True
    sofTransfer := True
  } elsewhen (clearToSend) {
    // Not in SOF gaurd band region or SOF disabled?
    when(tokenStart) {
      // Flush un-used previous Rx data
      fifoFlush := True
      inTransfer := tokenIn
      respExpected := tokenAck
      transferStart := True
      sofTransfer := False
      transferReqAck := True
    }
  }

  //-----------------------------------------------------------------
  // SOF Frame Number
  //-----------------------------------------------------------------
  // Time to send another SOF token?
  when(sendSof) {
    sofTime := SOF_ZERO
    sofValue := sofValue + 1
    // Start of frame interrupt
    sofIrq := True
  } otherwise {
    // Increment the SOF timer
    when(sofTime =/= SOF_THRESHOLD) { sofTime := sofTime + SOF_INC }
    sofIrq := False
  }

  //-----------------------------------------------------------------
  // Record Errors
  //-----------------------------------------------------------------
  val usbErr = RegInit(False) setWhen (io.utmi.rx.error) clearWhen (regUsbCtrlWr)

  //-----------------------------------------------------------------
  // Interrupts
  //-----------------------------------------------------------------
  val regIntrs = RegInit(USBIrq().getZero)
  val regIntrValid = RegNext((regIntrs.asBits & irqMask.asBits).orR, False)
  regIntrs.done clearWhen (irqAck.done)
  regIntrs.sof clearWhen (irqAck.sof) setWhen (sofIrq)
  regIntrs.err clearWhen (irqAck.err) // setWhen(False && !regErrCond)
  regIntrs.deviceDetect clearWhen (irqAck.deviceDetect) setWhen (io.utmi.lineState =/= USBLineState.SE0)
  io.intr := regIntrValid

  //-----------------------------------------------------------------
  // SIE
  //-----------------------------------------------------------------

  //-----------------------------------------------------------------
  // Data delay (to strip the CRC16 trailing bytes)
  //-----------------------------------------------------------------
  val shiftEn = io.utmi.rx.valid || !io.utmi.rx.active
  val rxData = Delay(io.utmi.data.i, 4, when = shiftEn, init = B(0, 8 bits))
  val regDataValid = RegInit(B(0, 4 bits))
  when(shiftEn) {
    regDataValid := (io.utmi.rx.valid && io.utmi.rx.active) ## (regDataValid >> 1)
  } otherwise {
    regDataValid(0) := False
  }
  val dataReady = regDataValid(0)
  val crcByte = Delay(!io.utmi.rx.active, 2, shiftEn, False)
  val rxActive = Delay(io.utmi.rx.active, 4, init = False)
  val rxActiveRise = io.utmi.rx.active.rise(False)

  //-----------------------------------------------------------------
  // Tx Token
  //-----------------------------------------------------------------
  val txToken = RegInit(B(0, 16 bits))
  val tokenRev = txToken.reversed
  crc5.io.crcIn.setAll()
  crc5.io.data := txToken(15 downto 5)
  val crc5Next = ~crc5.io.crcOut
  crc16.io.data := txFifo.io.pop.payload

  //-----------------------------------------------------------------
  // Tx EOP - detect end of transmit (token, data or ACK/NAK)
  //-----------------------------------------------------------------
  // End of transmit detection
  val waitEop = RegInit(False)
  val regLineState = RegNext(io.utmi.lineState, USBLineState.SE0())
  // SE0 filtering (2 cycles FS)
  val se0Detect =
    RegNext(io.utmi.lineState === USBLineState.SE0 && regLineState === USBLineState.SE0, False)
  // TODO: This needs updating for HS USB...
  val eopDetected = se0Detect && io.utmi.lineState =/= USBLineState.SE0

  //-----------------------------------------------------------------
  // Transmit / Receive counter
  //-----------------------------------------------------------------
  val byteCount = RegInit(U(0, 16 bits))

  //-----------------------------------------------------------------
  // Record request details
  //-----------------------------------------------------------------
  val sieInTransfer = RegInit(False)
  val sendAck, sendData1 = RegInit(False)
  val sieSendSof = RegInit(False)

  //-----------------------------------------------------------------
  // Response expected
  //-----------------------------------------------------------------
  val waitResp = RegInit(False)

  //-----------------------------------------------------------------
  // Status
  //-----------------------------------------------------------------
  val response = RegInit(B(0, 8 bits))
  val timeout, rxDone, txDone, crcErr = RegInit(False)

  //-----------------------------------------------------------------
  // Tx Timer
  //-----------------------------------------------------------------
  val lastTxTime = Counter(RX_TIMEOUT + 1)
  when(io.utmi.tx.fire) {
    // Start counting from last Tx
    lastTxTime.clear()
  } elsewhen (!lastTxTime.willOverflowIfInc) {
    // Increment the Tx timeout
    lastTxTime.increment()
  }
  // Response timeout (no response after 500uS from transmit)
  val rxRespTimeout = lastTxTime.willOverflowIfInc && waitResp

  // CRC control / check
  val crcSum = RegInit(Bits(16 bits).setAll())
  val crcError = False
  crc16.io.crcIn := crcSum

  io.utmi.tx.valid := False
  io.utmi.data.o := 0
  io.utmi.data.t := !io.utmi.tx.valid
  txFifo.io.pop.ready := False

  val fsm = new StateMachine {
    setEntry(stateBoot)
    disableAutoStart()
    val RX_DATA = new State
    val TX_PID, TX_DATA, TX_CRC1, TX_CRC2 = new State
    val TX_TOKEN1, TX_TOKEN2, TX_TOKEN3 = new State
    val TX_ACKNAK = new State
    val TX_WAIT, RX_WAIT, TX_IFS = new State
    // IDLE
    stateBoot.whenIsActive {
      sieIdle := True
      lastTxTime.clear() // Start counting from last Tx
      txToken := tokenDev ## tokenEp ## B(0, 5 bits)
      rxDone := False
      txDone := False
      when(transferStart) {
        // Start of new request
        // Transfer request
        // e.g. (H)SOF                                   [sof_transfer_i]
        //      (H)OUT + (H)DATA + (F)ACK/NACK/STALL     [data_len_i >= 0 && !in_transfer_i]
        //      (H)IN  + (F)DATA + (H)ACK                [in_transfer_i]
        //      (H)IN  + (F)NAK/STALL                    [in_transfer_i]
        sieInTransfer := inTransfer
        sendAck := inTransfer && respExpected
        sendData1 := tokenPidDatax
        sieSendSof := sofTransfer
        waitResp := respExpected
        when(!sofTransfer) {
          // New transfer request (not automatic SOF request)
          byteCount := txLen
          response := 0
          timeout := False
          // Clear error flag!
          crcErr := False
        }
        goto(TX_TOKEN1)
      }
    }
    TX_TOKEN1.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := tokenPid
      when(io.utmi.tx.ready) {
        startAck := True
        txToken(4 downto 0) := crc5Next
        goto(TX_TOKEN2)
      }
    }
    TX_TOKEN2.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := tokenRev(7 downto 0)
      when(io.utmi.tx.ready) { goto(TX_TOKEN3) }
    }
    TX_TOKEN3.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := tokenRev(15 downto 8)
      when(io.utmi.tx.ready) {
        when(sieSendSof) {
          // SOF - no data packet
          goto(TX_IFS)
        } elsewhen (sieInTransfer) {
          // IN - wait for data
          goto(RX_WAIT)
        } otherwise {
          // OUT/SETUP - Send data or ZLP
          goto(TX_IFS)
        }
      }
    }
    TX_IFS.whenIsActive {
      when(!ifsBusy) {
        // IFS expired
        when(sieSendSof) {
          // SOF - no data packet
          goto(stateBoot)
        } otherwise {
          // OUT/SETUP - Send data or ZLP
          goto(TX_PID)
        }
      }
    }
    TX_PID.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := Mux(sendData1, B(PID_DATA1, 8 bits), B(PID_DATA0, 8 bits))
      // First byte is PID (not CRC'd), reset CRC16
      crcSum.setAll()
      when(io.utmi.tx.ready) {
        // Last data byte sent?
        when(byteCount =/= 0) {
          // Count down data left to send
          byteCount := byteCount - 1
          goto(TX_DATA)
        } otherwise {
          goto(TX_CRC1)
        }
      }
    }
    TX_DATA.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := txFifo.io.pop.payload
      // Data sent?
      when(io.utmi.tx.ready) {
        txFifo.io.pop.ready := True
        // Next CRC start value
        crcSum := crc16.io.crcOut
        // Last data byte sent?
        when(byteCount =/= 0) {
          // Count down data left to send
          byteCount := byteCount - 1
        } otherwise {
          goto(TX_CRC1)
        }
      }
    }
    TX_CRC1.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := ~crcSum(7 downto 0)
      when(io.utmi.tx.ready) {
        goto(TX_CRC2)
      }
    }
    TX_CRC2.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := ~crcSum(15 downto 8)
      // Data sent?
      when(io.utmi.tx.ready) {
        when(waitResp) {
          // If a response is expected
          goto(RX_WAIT)
        } otherwise {
          // No response expected (e.g ISO transfer)
          txDone := True
          goto(stateBoot)
        }
      }
    }

    RX_WAIT.whenIsActive {
      crcSum.setAll() // Reset CRC16
      byteCount := 0
      txDone := False
      // Data received?
      when(dataReady) {
        waitResp := False
        // Store response PID
        response := rxData
        goto(RX_DATA)
      } elsewhen (rxRespTimeout) {
        // Waited long enough?
        goto(stateBoot)
      }
      when(rxRespTimeout) {
        // Waited long enough?
        timeout := True
      }
    }
    RX_DATA.whenIsActive {
      crc16.io.data := rxData
      rxDone := !io.utmi.rx.active // Receive complete
      // CRC16 error on received data
      crcError := !rxActive && sieInTransfer && (response === PID_DATA0 || response === PID_DATA1) && crcSum =/= 0xb001
      // Data received?
      when(dataReady) {
        crcSum := crc16.io.crcOut // Next CRC start value
        when(!crcByte) {
          // Received byte
          byteCount := byteCount + 1
        }
      } elsewhen (!rxActive) {
        // Receive complete
        // If some data received, check CRC
        crcErr := crcError
      }
      when(!rxActive) {
        // Receive complete
        when(sendAck && crcError) {
          // Send ACK but incoming data had CRC error, do not ACK
          goto(stateBoot)
        } elsewhen (sendAck && (response === PID_DATA0 || response === PID_DATA1)) {
          // Send an ACK response without CPU interaction?
          goto(TX_WAIT)
        } otherwise {
          goto(stateBoot)
        }
      }
    }

    TX_WAIT.whenIsActive {
      // Waited long enough?
      when(!ifsBusy) { goto(TX_ACKNAK) }
    }
    TX_ACKNAK.whenIsActive {
      io.utmi.tx.valid := True
      io.utmi.data.o := PID_ACK
      when(io.utmi.tx.ready) { goto(stateBoot) }
    }

    TX_CRC2.onExit { waitEop := True }
    TX_TOKEN3.onExit { waitEop := True }
    TX_ACKNAK.onExit { waitEop := True }

    val stateListForStatus = Seq(stateBoot, RX_WAIT, RX_DATA, TX_CRC2)
    when(!stateListForStatus.map(isActive(_)).orR) {
      rxDone := False
      txDone := False
    }
  }
  waitEop setWhen (rxActiveRise)
  waitEop clearWhen (eopDetected)

  val txIfs = RegInit(U(0, TX_IFS_W bits))
  when(waitEop || eopDetected) {
    // Start counting down from last Tx or EOP being detected at end of Rx
    txIfs := TX_IFS_V
  } elsewhen (txIfs =/= 0) {
    // Decrement IFS counter
    txIfs := txIfs - 1
  }
  // Tx/Rx -> Tx IFS timeout
  val ifsBusy = waitEop || txIfs =/= 0

  // Push incoming data into FIFO (not PID or CRC)
  rxFifo.io.push.valid := !fsm.isActive(fsm.stateBoot) &&
    !fsm.isActive(fsm.RX_WAIT) && dataReady && !crcByte
  rxFifo.io.push.payload := rxData

  //-----------------------------------------------------------------
  // Read mux
  //-----------------------------------------------------------------
  ctrlRegs.askRead := readEn
  ctrlRegs.readAddress := io.cfg.ar.addr(7 downto 0)

  //-----------------------------------------------------------------
  // Register usb_status
  //-----------------------------------------------------------------
  ctrlRegs.r(USB_STATUS, 16 -> sofTime, 2 -> usbErr, 0 -> io.utmi.lineState)

  //-----------------------------------------------------------------
  // Register usb_irq_sts
  //-----------------------------------------------------------------
  val irqSts = CombInit(regIntrs)
  irqSts.deviceDetect.allowOverride
  irqSts.deviceDetect := False
  ctrlRegs.r(USB_IRQ_STS, 0 -> irqSts)

  //-----------------------------------------------------------------
  // Register usb_rx_stat
  //-----------------------------------------------------------------
  ctrlRegs.r(USB_RX_STAT, 31 -> tokenStart, 30 -> crcErr, 29 -> timeout, 28 -> sieIdle)
  ctrlRegs.r(USB_RX_STAT, 16 -> response, 0 -> byteCount)

  //-----------------------------------------------------------------
  // Register usb_rd_data
  //-----------------------------------------------------------------
  ctrlRegs.r(USB_RW_DATA, 0 -> rxFifo.io.pop.payload)
  rxFifo.io.pop.ready := False
  ctrlRegs.onRead(USB_RW_DATA) {
    rxFifo.io.pop.ready := True
  }
}

object USBHost {
  def main(args: Array[String]): Unit = {
    SpinalConfig(
      defaultConfigForClockDomains = ClockDomainConfig(resetKind = ASYNC, resetActiveLevel = LOW),
      defaultClockDomainFrequency = FixedFrequency(60 MHz),
      targetDirectory = "generated_verilog"
    ).generateVerilog(new USBHost)
  }
}
