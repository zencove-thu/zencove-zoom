package zencove.core

import zencove.builder._
import spinal.core._
import spinal.lib._
import zencove.model._
import zencove.ZencoveConfig
import zencove.enum._
import zencove.util.BWord
import zencove.MIPS32
import zencove.util.UWord

class IntExecute(val config: ZencoveConfig, val fuIdx: Int) extends Plugin[ExecutePipeline] {
  private val withBRU = config.intIssue.bruIdx == fuIdx
  private val withCP0 = config.intIssue.cp0Idx == fuIdx
  private val iqDepth = config.intIssue.depth
  private val rPorts = config.regFile.rPortsEachInst
  private val prfAddrWidth = config.regFile.prfAddrWidth
  val rrdReq = Vec(UInt(prfAddrWidth bits), rPorts)
  var rrdRsp: Vec[Bits] = null
  val bypassReq = Vec(UInt(prfAddrWidth bits), rPorts)
  var bypassRsp: Vec[Flow[Bits]] = null
  var clrBusy: Flow[UInt] = null
  var wPort: Flow[RegWrite] = null
  var robWriteBRU: Flow[ROBStateBRUPort] = null
  var robWrite: Flow[ROBStateALUPort] = null
  object ISSUE_SLOT extends Stageable(IntIssueSlot(config))
  object REG_READ_RSP extends Stageable(Vec(BWord(), rPorts))
  object EXE_RESULT extends Stageable(BWord())
  object OVERFLOW extends Stageable(Bool)
  object WRITE_REG extends Stageable(Flow(UInt(prfAddrWidth bits)))
  object ROB_IDX extends Stageable(UInt(config.rob.robAddressWidth bits))

  object ACTUAL_TARGET extends Stageable(UWord())
  object ACTUAL_TAKEN extends Stageable(Bool)
  object MISPREDICT extends Stageable(Bool)
  object TRAP extends Stageable(Bool)
  object COND_TRUE extends Stageable(Bool)

  val issReqs = Vec(Bool, iqDepth)
  var issGrant: Vec[Bool] = null
  override def setup(pipeline: ExecutePipeline): Unit = {
    val PRF = pipeline.globalService[PhysRegFile]
    val IQ = pipeline.globalService[IntIssueQueue]
    val ROB = pipeline.globalService[ROBFIFO]
    rrdRsp = Vec(rrdReq.map(PRF.readPort(_)))
    clrBusy = PRF.clearBusy
    wPort = PRF.writePort(true)
    issGrant = IQ.grantPort(issReqs)
    val BYPASS = pipeline.globalService[BypassNetwork]
    BYPASS.writePorts += wPort
    bypassRsp = Vec(bypassReq.map(BYPASS.readPort(_)))
    if (withBRU || withCP0) robWriteBRU = ROB.bruPort
    else robWrite = ROB.aluPort
  }
  override def build(pipeline: ExecutePipeline): Unit = {
    val flush = pipeline.globalService[CommitFlush].regFlush
    pipeline plug new Area {
      // pipeline.stages.last.arbitration.flushIt setWhen flush
      pipeline.stages.drop(1).foreach(_.arbitration.removeIt setWhen flush)
    }
    pipeline.ISS plug new Area {
      import pipeline.ISS._
      val IQ = pipeline.globalService[IntIssueQueue]
      require(rPorts == 2)

      val validVec = Vec(Bool, iqDepth)
      val wakenVec = Vec(Bool, iqDepth)
      val matchVec = Vec(Bool, iqDepth)

      for (i <- 0 until iqDepth) {
        val slot = IQ.queue(i).payload
        val waken = (0 until rPorts).map { j => slot.rRegs(j).valid }.andR
        val fuMatch = True
        if (!withBRU) fuMatch clearWhen slot.uop.fuType === FUType.CMP
        if (!withCP0) fuMatch clearWhen slot.uop.fuType === FUType.CP0
        // 计算每个槽是否valid
        issReqs(i) := IQ.queue(i).valid && fuMatch && waken
        
        validVec(i) := IQ.queue(i).valid
        wakenVec(i) := waken
        matchVec(i) := fuMatch
      }
      // one-hot插入issue slot，并设置valid
      val issSlot = insert(ISSUE_SLOT)
      issSlot := MuxOH(issGrant, IQ.queue.map(_.payload))
      val issValid = issGrant.orR
      IQ.issueFire clearWhen arbitration.isStuck
      arbitration.removeIt setWhen (arbitration.notStuck && (flush || !issValid))
      // 本地bypass唤醒
      when(issValid && issSlot.uop.doRegWrite) {
        for (i <- 0 until iqDepth) {
          for (j <- 0 until rPorts)
            when(issSlot.wReg === IQ.queueNext(i).rRegs(j).payload && arbitration.notStuck) {
              // bypass wake-up with bypass network
              if (config.intIssue.alterImpl) IQ.queue(i).rRegs(j).valid := True
              IQ.queueNext(i).rRegs(j).valid := True
            }
        }
      }
    }
    pipeline.RRD plug new Area {
      import pipeline.RRD._
      val issSlot = input(ISSUE_SLOT)
      for (i <- 0 until rPorts) {
        rrdReq(i) := issSlot.rRegs(i).payload
        insert(REG_READ_RSP)(i) := rrdRsp(i)
      }
      // 远程唤醒
      clrBusy.valid := arbitration.isValidNotStuck && issSlot.uop.doRegWrite
      clrBusy.payload := issSlot.wReg

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
      val regData = CombInit(rrdRsp)
      // bypass logic
      for (i <- 0 until rPorts) {
        bypassReq(i) := issSlot.rRegs(i).payload
        when(bypassRsp(i).valid) { regData(i) := bypassRsp(i).payload }
      }
      val exeResult = insert(EXE_RESULT)
      //ALU section
      val alu = new ALU(config.isa)
      alu.io.src1 := regData(0).asUInt
      val fields = InstructionParser(issSlot.uop.partialInst)
      val imm = issSlot.uop.immExtendType
        .mux(ExtendType.SIGN -> fields.signExtendImm, ExtendType.ZERO -> fields.zeroExtendImm)
      alu.io.src2 := Mux(issSlot.uop.useRt, regData(1).asUInt, imm)
      alu.io.sa := Mux(issSlot.uop.useRs, regData(0).asUInt(4 downto 0), fields.sa)
      alu.io.op := issSlot.uop.aluOp

      // default result
      exeResult := alu.io.result.asBits

      if (withBRU) {
        //BRU Section
        val bru = new BRU
        val comparator = new Comparator(config.isa)
        comparator.io.src1 := regData(0).asUInt
        comparator.io.src2 := Mux(issSlot.uop.useRt, regData(1).asUInt, imm)
        comparator.io.op := issSlot.uop.cmpOp

        bru.io.predictJump := issSlot.uop.predInfo.predictTaken
        bru.io.predictAddr := issSlot.uop.predInfo.predictAddr
        bru.io.isBranch := issSlot.uop.isBranch
        bru.io.isJR := issSlot.uop.isJR
        bru.io.isJump := issSlot.uop.isJump
        bru.io.pc := issSlot.uop.intIQpc @@ U(0, 2 bits)
        bru.io.inst := issSlot.uop.partialInst
        bru.io.condition := comparator.io.result
        bru.io.regSrc := regData(0).asUInt

        insert(ACTUAL_TARGET) := bru.io.actualTarget
        insert(ACTUAL_TAKEN) := comparator.io.result || issSlot.uop.isJR || issSlot.uop.isJump
        insert(MISPREDICT) := bru.io.mispredict

        when(issSlot.uop.branchLike) {
          exeResult := ((issSlot.uop.intIQpc @@ U(0, 2 bits)) + 8).asBits
        }

        if (config.isa.useTrap) insert(TRAP) := comparator.io.result && issSlot.uop.isTrap
        if (config.isa.useCondMove) {
          insert(COND_TRUE) := True
          when(issSlot.uop.isCondMove) {
            exeResult := regData(0)
            val nez = regData(1).orR
            switch(issSlot.uop.cmpOp) {
              is(CompareOp.EQZ) { insert(COND_TRUE) := !nez }
              is(CompareOp.NEZ) { insert(COND_TRUE) := nez }
            }
          }
        }
      }

      if (withCP0) {
        // CP0 section
        val CP0 = pipeline.globalService[CP0Regs]
        CP0.isReadInst setWhen (arbitration.isValid && issSlot.uop.readCP0)
        CP0.cp0RAddr := fields.cp0Addr
        CP0.readCommit clearWhen (arbitration.isStuck)
        when(issSlot.uop.readCP0) { exeResult := CP0.readData }
        // MTC0 uses rt
        if (withBRU) {
          when(issSlot.uop.writeCP0) { insert(ACTUAL_TARGET) := regData(1).asUInt }
        } else {
          insert(ACTUAL_TARGET) := regData(1).asUInt
        }
      }

      insert(WRITE_REG).valid := issSlot.uop.doRegWrite
      insert(OVERFLOW) := issSlot.uop.genOverflow && alu.io.overflow
      insert(WRITE_REG).payload := issSlot.wReg
      insert(ROB_IDX) := issSlot.robIdx
    }
    pipeline.WB plug new Area {
      import pipeline.WB._
      val wbReq = input(WRITE_REG)
      val wbData = input(EXE_RESULT)
      val robIdx = input(ROB_IDX)
      val overflow = input(OVERFLOW)
      wPort.valid := arbitration.isValidNotStuck && wbReq.valid
      wPort.payload.addr := wbReq.payload
      wPort.payload.data := wbData

      val except = Flow(ExceptionPayload(false)).setIdle()
      when(overflow) {
        except.valid := True
        except.payload.code := MIPS32.ExceptionCode.overflow
      }
      if (withBRU && config.isa.useTrap) {
        val trap = input(TRAP)
        when(trap) {
          except.valid := True
          except.payload.code := MIPS32.ExceptionCode.trap
        }
      }

      val ROB = pipeline.globalService[ROBFIFO]
      if (withBRU || withCP0) {
        robWriteBRU.valid := arbitration.isValidNotStuck
        robWriteBRU.robIdx := robIdx
        robWriteBRU.except := except
        robWriteBRU.intResult := input(ACTUAL_TARGET)
        robWriteBRU.mispredict := input(MISPREDICT)
        robWriteBRU.condTrue := (if (withBRU && config.isa.useCondMove) input(COND_TRUE) else True)
        robWriteBRU.actualTaken := input(ACTUAL_TAKEN)
      } else {
        robWrite.valid := arbitration.isValidNotStuck
        robWrite.robIdx := robIdx
        robWrite.except := except
        // robWrite.intResult := wbData.asUInt
      }
    }
  }
}
