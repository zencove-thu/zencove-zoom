package zencove.core

import zencove.builder._
import spinal.core._
import spinal.lib._
import zencove.model._
import zencove.ZencoveConfig
import zencove.enum._
import zencove.util._
import zencove.MIPS32
import zencove.blackbox

class MulDivExecute(val config: ZencoveConfig) extends Plugin[ExecutePipeline] {
  private val iqDepth = config.mulDiv.depth
  private val rPorts = config.regFile.rPortsEachInst
  private val prfAddrWidth = config.regFile.prfAddrWidth
  val rrdReq = Vec(UInt(prfAddrWidth bits), rPorts)
  var rrdRsp: Vec[Bits] = null
  var clrBusy: Flow[UInt] = null
  var wPort: Flow[RegWrite] = null
  var robWrite: Flow[UInt] = null

  object ISSUE_SLOT extends Stageable(MulDivIssueSlot(config))
  object REG_READ_RSP extends Stageable(Vec(BWord(), rPorts))
  object HLU_READ_RSP extends Stageable(Bits(64 bits))
  object EXE_RESULT extends Stageable(Bits(64 bits))

  override def setup(pipeline: ExecutePipeline): Unit = {
    val PRF = pipeline.globalService[PhysRegFile]
    val IQ = pipeline.globalService[MulDivIssueQueue]
    val ROB = pipeline.globalService[ROBFIFO]
    rrdRsp = Vec(rrdReq.map(PRF.readPort(_)))
    clrBusy = PRF.clearBusy
    wPort = PRF.writePort(true)
    robWrite = ROB.completePort
  }
  override def build(pipeline: ExecutePipeline): Unit = {
    val HLPRF = pipeline.globalService[HiLoRegFile]
    val flush = pipeline.globalService[CommitFlush].regFlush
    pipeline plug new Area {
      // pipeline.stages.last.arbitration.flushIt setWhen flush
      pipeline.stages.drop(1).foreach(_.arbitration.removeIt setWhen flush)
    }
    pipeline.ISS plug new Area {
      import pipeline.ISS._
      val IQ = pipeline.globalService[MulDivIssueQueue]
      require(rPorts == 2)
      for (i <- 0 to 0) {
        val slot = IQ.queue(i).payload
        val waken = (0 until rPorts).map { j => slot.rRegs(j).valid }.andR
        // 计算每个槽是否valid
        // HI/LO寄存器只有本队列读写，oldest first配合前传不需要判断唤醒
        // TODO: 可以考虑部分乱序（但是要保证Hi/Lo的RAW正确）
        IQ.issueReq := IQ.queue(i).valid && waken
      }
      // one-hot插入issue slot，并设置valid
      val issSlot = insert(ISSUE_SLOT)
      issSlot := IQ.queue(0).payload
      val issValid = IQ.issueReq
      IQ.issueFire clearWhen arbitration.isStuck
      arbitration.removeIt setWhen (arbitration.notStuck && (flush || !issValid))
    }
    pipeline.RRD plug new Area {
      import pipeline.RRD._
      val issSlot = input(ISSUE_SLOT)
      for (i <- 0 until rPorts) {
        rrdReq(i) := issSlot.rRegs(i).payload
        insert(REG_READ_RSP)(i) := rrdRsp(i)
      }
      // 读寄存器=写寄存器-1
      HLPRF.readAddr := issSlot.hiLoReg - 1
      insert(HLU_READ_RSP) := HLPRF.readRsp
      // Hi&Lo bypass, 不需要判断写地址，因为HiLo只有这个流水线写
      val exeSlot = pipeline.EXE.input(ISSUE_SLOT)
      when(pipeline.EXE.arbitration.isValid && exeSlot.uop.writeHiLo) {
        output(HLU_READ_RSP) := pipeline.EXE.output(EXE_RESULT)
      }

      // memory推测唤醒
      if (config.useSpeculativeWakeup) {
        arbitration.haltByOther setWhen pipeline
          .globalService[SpeculativeWakeupHandler]
          .regWakeupFailed
      }

    }
    pipeline.EXE plug new Area {
      import pipeline.EXE._
      val issSlot = input(ISSUE_SLOT)
      val rrdRsp = input(REG_READ_RSP)
      // no bypass
      val regData = rrdRsp
      val exeResult = insert(EXE_RESULT)
      val isMultiply = arbitration.isValidOnEntry && issSlot.uop.fuType === FUType.MUL
      val isDivision = arbitration.isValidOnEntry && issSlot.uop.fuType === FUType.DIV

      val isSigned = issSlot.uop.signed === Signed.S

      val rs = regData(0).asSInt
      val rt = regData(1).asSInt
      val absRs = rs.abs(isSigned)
      val absRt = rt.abs(isSigned)
      val hiLoData = input(HLU_READ_RSP)

      val wakeupCycle = CombInit(arbitration.notStuck)

      val multiplier = new blackbox.Multiplier()
      multiplier.io.A := absRs
      multiplier.io.B := absRt
      val mulCounter = Counter(config.mulDiv.multiplyLatency + 1)
      val mulResult =
        multiplier.io.P.twoComplement(isSigned && (rs.sign ^ rt.sign)).asBits(0, 64 bits)
      when(isMultiply) {
        mulCounter.increment()
        arbitration.haltItself setWhen (!mulCounter.willOverflowIfInc)
        // assume never halt by others
        wakeupCycle := mulCounter === config.mulDiv.multiplyLatency - 1
      }

      val isFirstCycle = RegNext(arbitration.notStuck)
      val useEarlyOut = config.mulDiv.useDivisionEarlyOut
      val smallDivSize = config.mulDiv.divisionEarlyOutWidth
      val in16Bits =
        if (useEarlyOut)
          absRs(31 downto smallDivSize) === 0 && absRt(31 downto smallDivSize) === 0
        else
          False
      val (quotient, remainder) = if (config.mulDiv.blackboxDivider) {
        val divider = new blackbox.Divider()
        divider.io.dividend.valid := isDivision && isFirstCycle && !in16Bits
        divider.io.dividend.payload.data := absRs.asBits
        divider.io.divisor.valid := isDivision && isFirstCycle && !in16Bits
        divider.io.divisor.payload.data := absRt.asBits
        val absQuotient = UInt(32 bits)
        val absRemainder = UInt(32 bits)
        if (useEarlyOut) {
          val divider16 = new blackbox.Divider(dataWidth = smallDivSize, name = "divider16")
          divider16.io.dividend.valid := isDivision && isFirstCycle
          divider16.io.dividend.payload.data := absRs.asBits.resized
          divider16.io.divisor.valid := isDivision && isFirstCycle
          divider16.io.divisor.payload.data := absRt.asBits.resized
          when(isDivision) {
            arbitration.haltItself setWhen (!in16Bits && !divider.io.dout.valid)
            arbitration.haltItself setWhen (in16Bits && !divider16.io.dout.valid)
          }
          when(in16Bits) {
            absQuotient := divider16.io.dout.payload.data
              .asUInt(divider16.alignedDataWidth, smallDivSize bits)
              .resized
            absRemainder := divider16.io.dout.payload.data.asUInt(0, smallDivSize bits).resized
          } otherwise {
            absQuotient := divider.io.dout.payload.data.asUInt(32, 32 bits)
            absRemainder := divider.io.dout.payload.data.asUInt(0, 32 bits)
          }
        } else {
          when(isDivision) {
            arbitration.haltItself setWhen (!in16Bits && !divider.io.dout.valid)
          }
          absQuotient := divider.io.dout.payload.data.asUInt(32, 32 bits)
          absRemainder := divider.io.dout.payload.data.asUInt(0, 32 bits)
        }
        val quotient = absQuotient.twoComplement(isSigned && (rs.sign ^ rt.sign)).asBits(0, 32 bits)
        val remainder = absRemainder.twoComplement(isSigned && rs.sign).asBits(0, 32 bits)
        (quotient, remainder)
      } else {
        val divider = new math.UnsignedDivider(32, 32, false)
        divider.io.flush := False // TODO: flush could be set consistently with halt
        divider.io.cmd.valid := isDivision && isFirstCycle && !in16Bits
        divider.io.cmd.numerator := absRs
        divider.io.cmd.denominator := absRt
        divider.io.rsp.ready := !arbitration.isStuckByOthers
        val absQuotient = UInt(32 bits)
        val absRemainder = UInt(32 bits)

        if (useEarlyOut) {
          val divider16 = new math.UnsignedDivider(16, 16, false)
          divider16.io.flush := False
          divider16.io.cmd.valid := isDivision && isFirstCycle
          divider16.io.cmd.numerator := absRs.resized
          divider16.io.cmd.denominator := absRt.resized
          divider16.io.rsp.ready := !arbitration.isStuckByOthers

          when(isDivision) {
            arbitration.haltItself setWhen (!in16Bits && !divider.io.rsp.valid)
            arbitration.haltItself setWhen (in16Bits && !divider16.io.rsp.valid)
          }
          when(in16Bits) {
            absQuotient := divider16.io.rsp.quotient.resized
            absRemainder := divider16.io.rsp.remainder.resized
          } otherwise {
            absQuotient := divider.io.rsp.quotient
            absRemainder := divider.io.rsp.remainder
          }
        } else {
          when(isDivision) {
            arbitration.haltItself setWhen (!divider.io.rsp.valid)
          }
          absQuotient := divider.io.rsp.quotient
          absRemainder := divider.io.rsp.remainder
        }
        val quotient = absQuotient.twoComplement(isSigned && (rs.sign ^ rt.sign)).asBits(0, 32 bits)
        val remainder = absRemainder.twoComplement(isSigned && rs.sign).asBits(0, 32 bits)
        (quotient, remainder)
      }

      exeResult.assignDontCare()
      switch(issSlot.uop.fuType) {
        is(FUType.MUL) {
          exeResult := mulResult
          switch(issSlot.uop.opHiLo) {
            import HiLoWriteType._
            is(WRITE) { exeResult := mulResult }

            if (config.isa.useMADD) {
              is(ADD) { exeResult := (hiLoData.asUInt + mulResult.asUInt).asBits }
              is(SUB) { exeResult := (hiLoData.asUInt - mulResult.asUInt).asBits }
            }

          }
        }
        is(FUType.DIV) {
          exeResult := remainder ## quotient
        }
        is(FUType.HLU) {
          when(issSlot.uop.writeHiLo) {
            // MTHI/MTLO
            when(isSigned) {
              exeResult(0, 32 bits) := rs.asBits
              exeResult(32, 32 bits) := hiLoData(32, 32 bits)
            } otherwise {
              exeResult(0, 32 bits) := hiLoData(0, 32 bits)
              exeResult(32, 32 bits) := rs.asBits
            }
          } otherwise {
            // MFHI/MFLO
            exeResult(0, 32 bits) := Mux(isSigned, hiLoData(0, 32 bits), hiLoData(32, 32 bits))
          }
        }
      }

      // 远程唤醒
      clrBusy.valid := arbitration.isValid && wakeupCycle && issSlot.uop.doRegWrite
      clrBusy.payload := issSlot.wReg

    }
    pipeline.WB plug new Area {
      import pipeline.WB._
      val issSlot = input(ISSUE_SLOT)
      val exeResult = input(EXE_RESULT)
      val robIdx = issSlot.robIdx
      wPort.valid := arbitration.isValidNotStuck && issSlot.uop.doRegWrite
      wPort.payload.addr := issSlot.wReg
      wPort.payload.data := exeResult(0, 32 bits)
      HLPRF.writePort.valid := arbitration.isValidNotStuck && issSlot.uop.writeHiLo
      HLPRF.writePort.addr := issSlot.hiLoReg
      HLPRF.writePort.data := exeResult
      val ROB = pipeline.globalService[ROBFIFO]
      robWrite.valid := arbitration.isValidNotStuck
      robWrite.payload := robIdx

    }
  }
}
