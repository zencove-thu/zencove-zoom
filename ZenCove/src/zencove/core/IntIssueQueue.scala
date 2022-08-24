package zencove.core

import zencove.builder.Plugin
import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.model._
import zencove.enum._
import zencove.util._
import scala.collection.mutable
import zencove.builder.Stageable

/** 整数issue queue，不包括乘除法。采用压缩方法。
  */
class IntIssueQueue(config: ZencoveConfig)
    extends CompressedIQ(
      config.intIssue,
      config.decode.decodeWidth,
      HardType(IntIssueSlot(config))
    ) {
  private val issConfig = config.intIssue
  val rPorts = config.regFile.rPortsEachInst
  val busyAddrs = Vec(UInt(config.regFile.prfAddrWidth bits), decodeWidth * rPorts)
  def fuMatch(uop: MicroOp) = uop.fuType.isAnyOf(FUType.ALU, FUType.CMP, FUType.CP0)

  object PUSH_INDEXES extends Stageable(Vec(Flow(UInt(log2Up(decodeWidth) bits)), decodeWidth))
  override def build(pipeline: CPU): Unit = {
    pipeline.RENAME plug new Area {
      import pipeline.RENAME._
      val decPacket = input(pipeline.pDecode.signals.DECODE_PACKET)
      val enqueueMask = Bits(decodeWidth bits)
      val pushIndexes = insert(PUSH_INDEXES)
      for (i <- 0 until decodeWidth) {
        val valid = decPacket(i).valid
        val uop = decPacket(i).payload
        enqueueMask(i) := valid && fuMatch(uop) && !uop.except.valid
        // 在rename阶段计算互联
        pushIndexes(i).setIdle()
        if (i > 0) {
          val pushIdx = CountOne(enqueueMask.take(i))
          for (j <- 0 to i) when(pushIdx === j && enqueueMask(i))(pushIndexes(j).push(i))
        } else {
          when(enqueueMask(i))(pushIndexes(0).push(i))
        }
      }
    }
    pipeline.DISPATCH plug new Area {
      genIssueSelect()
      genGlobalWakeup(pipeline.service[PhysRegFile], rPorts)
      import pipeline.DISPATCH._
      // 唤醒逻辑：
      // 1. 入队唤醒（dispatch入口读busy）
      // 2. 远程唤醒（监听写busy广播）
      // 3. 本地唤醒（监听select结果）
      // 入队
      val decPacket = input(pipeline.pDecode.signals.DECODE_PACKET)
      val renameRecs = input(pipeline.pDecode.signals.RENAME_RECORDS)
      val robIdxs = input(pipeline.pDecode.signals.ROB_INDEXES)
      val pushIndexes = input(PUSH_INDEXES)
      val pushPorts = Vec(slotType, decodeWidth)
      for (i <- 0 until decodeWidth) {
        // slot处理
        val valid = decPacket(i).valid
        val uop = decPacket(i).payload
        val rename = renameRecs(i)
        val slot = pushPorts(i)
        slot.uop.assignSomeByName(uop)
        slot.uop.partialInst := uop.inst.resized
        slot.uop.intIQpc := uop.pc(31 downto 2)
        for (j <- 0 until rPorts) {
          slot.rRegs(j).payload := rename.rRegs(j)
          busyAddrs(i * rPorts + j) := rename.rRegs(j)
        }
        // 入队唤醒（dispatch入口读busy）
        slot.rRegs(0).valid := !uop.useRs || !busyRsps(i * rPorts)
        slot.rRegs(1).valid := !uop.useRt || !busyRsps(i * rPorts + 1)
        slot.wReg := rename.wReg
        slot.robIdx := robIdxs(i)

        // port互联，与rename相似
        val idx = pushIndexes(i)
        val port = queueIO.pushPorts(i)
        port.valid := arbitration.isValidNotStuck && idx.valid
        port.payload := pushPorts(idx.payload)
        arbitration.haltItself setWhen (arbitration.isValid && idx.valid && !port.ready)
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
}
