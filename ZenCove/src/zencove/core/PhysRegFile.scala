package zencove.core

import spinal.core._
import spinal.lib._
import zencove.builder.Plugin
import zencove.RegFileConfig
import zencove.util.BWord
import zencove.model._
import scala.collection.mutable

case class PRFWritePort(hw: Flow[RegWrite], bypass: Boolean)

class PhysRegFile(config: RegFileConfig) extends Plugin[CPU] {
  // 物理寄存器要经历如下状态：
  // 1. 被写分配，此后的指令都应该读新值，但是新值尚未写回。
  // 2. 写完成，此后的读都可以直接读寄存器。
  // 3. 释放，此后不应该有读发生。
  val regs = Vec(RegInit(BWord(0)), config.nPhysRegs)
  val busys = Vec(RegInit(False), config.nPhysRegs)
  private val writePorts = mutable.ArrayBuffer[PRFWritePort]()
  val clearBusys = mutable.ArrayBuffer[Flow[UInt]]()
  private val readPorts = mutable.ArrayBuffer[(UInt, Bits)]()
  private val readBusys = mutable.ArrayBuffer[(UInt, Bool)]()
  def writePort(bypass: Boolean) = {
    val port = Flow(RegWrite(config.prfAddrWidth)) //.setCompositeName(this, "writePort", true)
    writePorts += PRFWritePort(port, bypass)
    when(port.valid)(regs(port.payload.addr - 1) := port.payload.data)
    port
  }
  def clearBusy = {
    val port = Flow(UInt(config.prfAddrWidth bits))
    clearBusys += port
    when(port.valid)(busys(port.payload - 1) := False)
    port
  }
  def readPort(addr: UInt) = {
    val port = Mux(addr === 0, BWord(0), regs(addr - 1)) //.setCompositeName(this, "readPort", true)
    readPorts += (addr -> port)
    port
  }

  def readBusy(addr: UInt) = {
    val port = Mux(addr === 0, False, busys(addr - 1)) //.setCompositeName(this, "readBusy", true)
    readBusys += (addr -> port)
    port
  }
  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    if (pipeline.pDecode.serviceExist[RenameByPRF])
      pipeline.pDecode.service[RenameByPRF].freeList.io.pop.foreach { popPort =>
        // 分配出去的寄存器要标记为busy
        // 空闲寄存器的busy随便标，因此这么做似乎没有问题
        // 需要清busy的寄存器必然不在free list中，直到其生命周期结束
        when(popPort.fire)(busys(popPort.payload - 1) := True)
      }
    for (i <- 0 until config.nPhysRegs) {
      val writeOH = writePorts.map { p => p.hw.valid && p.hw.addr - 1 === i }
      when(writeOH.orR) { regs(i) := MuxOH(writeOH, writePorts.map(_.hw.payload.data)) }
    }
    // writeback前传逻辑
    val bypassWritePorts = writePorts.filter(_.bypass)
    readPorts.foreach { r =>
      // p0永远不应该被写，它是r0的固定映射
      // 永远不应该出现写冲突，rename处理掉了写冲突
      val writeOH = bypassWritePorts.map { p => p.hw.valid && p.hw.payload.addr === r._1 }
      when(writeOH.orR) { r._2 := MuxOH(writeOH, bypassWritePorts.map(_.hw.payload.data)) }
    }
    readBusys.foreach { r =>
      // busy需要被前传，这样write back就会唤醒正在进入IQ的指令
      val writeOH = clearBusys.map { p => p.valid && p.payload === r._1 }
      when(writeOH.orR) { r._2 := False }
    }
  }
}
