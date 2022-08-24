package zencove.core

import zencove.builder.Plugin
import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.model._
import zencove.enum._
import zencove.util._

class MulDivIssueQueue(config: ZencoveConfig)
    extends CompressedFIFO(
      config.mulDiv,
      config.decode.decodeWidth,
      HardType(MulDivIssueSlot(config))
    ) {
  private val issConfig = config.memIssue
  val rPorts = config.regFile.rPortsEachInst
  val busyAddrs = Vec(UInt(config.regFile.prfAddrWidth bits), decodeWidth * rPorts)
  def fuMatch(uop: MicroOp) = uop.fuType.isAnyOf(FUType.HLU, FUType.MUL, FUType.DIV)
  def build(pipeline: CPU): Unit = pipeline.DISPATCH plug new Area {
    genIssueSelect()
    genGlobalWakeup(pipeline.service[PhysRegFile], rPorts)
    import pipeline.DISPATCH._
    // 入队、唤醒与int IQ相同
    val decPacket = input(pipeline.pDecode.signals.DECODE_PACKET)
    val renameRecs = input(pipeline.pDecode.signals.RENAME_RECORDS)
    val hluRenameRecs = input(pipeline.pDecode.signals.HILO_RENAME_RECORDS)
    val robIdxs = input(pipeline.pDecode.signals.ROB_INDEXES)
    val enqueueMask = Bits(decodeWidth bits)
    queueIO.pushPorts.foreach(_.setIdle())
    for (i <- 0 until decodeWidth) {
      val valid = decPacket(i).valid
      val uop = decPacket(i).payload
      val rename = renameRecs(i)
      val pushPort = queueIO.pushPorts.dataType().setBlocked()
      val slot = pushPort.payload
      slot.uop.assignAllByName(uop)
      for (j <- 0 until rPorts) {
        slot.rRegs(j).payload := rename.rRegs(j)
        busyAddrs(i * rPorts + j) := rename.rRegs(j)
      }
      // 入队唤醒（dispatch入口读busy）
      slot.rRegs(0).valid := !uop.useRs || !busyRsps(i * rPorts)
      slot.rRegs(1).valid := !uop.useRt || !busyRsps(i * rPorts + 1)
      slot.wReg := rename.wReg
      // ROB不关心Hi/Lo寄存器号，因为FIFO提交，但是读写寄存器需要提供这个号给Hi/Lo PRF
      slot.hiLoReg := hluRenameRecs(i)
      slot.robIdx := robIdxs(i)
      val enqueue = valid && fuMatch(uop) && !uop.except.valid
      enqueueMask(i) := enqueue
      pushPort.valid := arbitration.isValidNotStuck && enqueue
      arbitration.haltItself setWhen (arbitration.isValid && enqueue && !pushPort.ready)

      // port互联，与rename相似
      if (i > 0) {
        val pushIdx = CountOne(enqueueMask.take(i))
        for (j <- 0 to i) when(pushIdx === j && enqueueMask(i))(pushPort >> queueIO.pushPorts(j))
      } else {
        when(enqueueMask(i))(pushPort >> queueIO.pushPorts(0))
      }
    }

    // flush最高优先级
    val flush = pipeline.globalService[CommitFlush].regFlush
    queueFlush setWhen flush
  }
  Component.current.afterElaboration {
    genEnqueueLogic()
    genCompressLogic()
    genFlushLogic()
  }
}
