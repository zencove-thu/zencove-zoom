package zencove.core

import zencove.builder.Plugin
import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.util._
import zencove.model._
import scala.math

class RenameHiLo(config: ZencoveConfig) extends Plugin[DecodePipeline] {
  private val rfConfig = config.regFile
  private val decodeWidth = config.decode.decodeWidth
  private val retireWidth = config.rob.retireWidth
  private val prfAddrWidth = config.hlu.prfAddrWidth
  val sRAT = RegInit(U(0, prfAddrWidth bits))
  val aRAT = RegInit(U(0, prfAddrWidth bits))
  val freeList = new Area {
    require(isPow2(config.hlu.nPhysRegs))
    val isRisingOccupancy = RegInit(True)
    // free list size = nPhysRegs - 1
    val isFull = aRAT === sRAT && isRisingOccupancy
    val pushPorts = Vec(Bool, retireWidth)
    val popPorts = Vec(Stream(UInt(prfAddrWidth bits)), decodeWidth)
    // push logic
    val pushCount = CountOne(pushPorts)
    aRAT := aRAT + pushCount

    // pop logic
    // max pop + 1
    val maxPop = aRAT - sRAT
    // 找到第一个非fire的
    val popCount = CountOne(popPorts.map(_.fire))
    for (i <- 0 until decodeWidth) {
      popPorts(i).valid := isFull || i + 1 < maxPop
      if (i > 0) {
        popPorts(i).payload := sRAT + 1 + CountOne(popPorts.take(i).map(_.ready))
      } else {
        popPorts(i).payload := sRAT + 1
      }
    }
    sRAT := sRAT + popCount

    when(pushCount =/= popCount)(isRisingOccupancy := pushCount > popCount)

    val recover = Bool
    when(recover) {
      sRAT := aRAT
      isRisingOccupancy := True
    }
  }
  override def build(pipeline: DecodePipeline): Unit = pipeline.RENAME plug new Area {
    import pipeline.RENAME._
    val decPacket = input(pipeline.signals.DECODE_PACKET)
    val wReqs = Vec(Bool, decodeWidth)
    val wRsps = Vec(UInt(prfAddrWidth bits), decodeWidth)
    // 读寄存器=写寄存器-1
    val noFreeRegs = False
    for (i <- 0 until decPacket.size; valid = decPacket(i).valid; uop = decPacket(i).payload) {
      val fields = InstructionParser(uop.inst)
      wReqs(i) := valid && uop.writeHiLo
      freeList.popPorts(i).ready := arbitration.isFiring && wReqs(i)
      wRsps(i) := freeList.popPorts(i).payload
      // freeList空了
      noFreeRegs setWhen (arbitration.isValid && wReqs(i) && !freeList.popPorts(i).valid)
    }
    // RENAME结果插入流水线（给ROB用）
    insert(pipeline.signals.HILO_RENAME_RECORDS) := wRsps
    arbitration.haltItself setWhen noFreeRegs

    val arfCommit = pipeline.globalService[ARFCommit]
    // 提交时，修改aRAT并释放sRAT
    freeList.pushPorts.zip(arfCommit.hiLoCommits).foreach { case (port, commit) => port := commit }
    // 分支预测恢复时，将aRAT拷贝进sRAT
    // 预测恢复时freeList也要恢复
    freeList.recover := arfCommit.recoverPRF
  }
}
