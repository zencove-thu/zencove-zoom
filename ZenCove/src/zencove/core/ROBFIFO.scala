package zencove.core

import zencove.builder.Plugin
import zencove.model._
import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.enum._
import zencove.util._
import scala.math
import spinal.lib.fsm._
import scala.collection.mutable.ArrayBuffer

class ROBFIFO(config: ZencoveConfig) extends Plugin[CPU] {
  private val prfAddressWidth = config.regFile.prfAddrWidth
  private val robDepth = config.rob.robDepth
  private val retireWidth = config.rob.retireWidth
  private val decodeWidth = config.decode.decodeWidth
  private val addressWidth = config.rob.robAddressWidth
  private val entryType = HardType(ROBEntry(config))
  private val infoType = HardType(ROBEntryInfo(config))
  private val stateType = HardType(ROBEntryState(config.regFile))
  require(isPow2(robDepth))
  val robInfo = new MultiPortFIFOSyncImpl(infoType, robDepth, decodeWidth, retireWidth) {
    val flush = in(Bool)
    when(flush) {
      pushPtr := 0
      popPtr := 0
      isRisingOccupancy := False
      // 可pop不可push，否则数据会丢失
      // fifoIO.push.foreach(_.setBlocked())
    }
    pushPtr.asOutput()
    popPtr.asOutput()
    popCount.asOutput()
  }

  // ROB state随机写口
  private val completePorts = ArrayBuffer[Flow[UInt]]()
  private val lsuPorts = ArrayBuffer[Flow[ROBStateLSUPort]]()
  private val bruPorts = ArrayBuffer[Flow[ROBStateBRUPort]]()
  private val aluPorts = ArrayBuffer[Flow[ROBStateALUPort]]()
  def completePort = {
    val port = Flow(UInt(config.rob.robAddressWidth bits))
    completePorts += port
    port
  }
  def lsuPort = {
    val port = Flow(ROBStateLSUPort(config))
    lsuPorts += port
    port
  }
  def bruPort = {
    val port = Flow(ROBStateBRUPort(config))
    bruPorts += port
    port
  }
  def aluPort = {
    val port = Flow(ROBStateALUPort(config))
    aluPorts += port
    port
  }
  val robState = Reg(Vec(stateType, robDepth))

  val pushPtr = robInfo.pushPtr
  val popPtr = robInfo.popPtr
  val fifoIO = new Bundle {

    /** push的valid，pop的ready，都要遵循连续性，从头开始的第一个0就表示了停止的位置，后面的1都会被忽略。
      */
    val push = Vec(Stream(ROBEntry(config, false)), decodeWidth)
    val pop = Vec(Stream(entryType), retireWidth)
    // 同步清空FIFO
    val flush = Bool
  }

  val debugPopWriteData = config.debug generate Vec(BWord(), retireWidth)
  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    if (config.debug) {
      val physRegs = pipeline.service[PhysRegFile].regs
      for (i <- 0 until retireWidth) {
        debugPopWriteData(i) := physRegs(fifoIO.pop(i).payload.info.rename.wReg)
      }
    }
    // FIFO与MultiPortFIFOVec完全一致，但是ROB不止FIFO端口
    // multi-port FIFO io
    for (i <- 0 until retireWidth)
      fifoIO.pop(i).translateFrom(robInfo.io.pop(i)) { (entry, info) =>
        entry.info := info
        entry.state.setAsReg().allowOverride
        entry.state := robState(popPtr + robInfo.popCount + i)
      }
    for (i <- 0 until decodeWidth) {
      robInfo.io.push(i).translateFrom(fifoIO.push(i)) { (info, entry) =>
        info := entry.info
      }
      when(fifoIO.push(i).fire) {
        robState(pushPtr + i).assignSomeByName(fifoIO.push(i).payload.state)
        for (j <- i until retireWidth) when(pushPtr + i === popPtr + robInfo.popCount + j) {
          fifoIO.pop(j).payload.state.assignSomeByName(fifoIO.push(i).payload.state)
        }
      }
    }
    robInfo.flush := fifoIO.flush
    // random write ports
    println("ROB port summary:")
    printf("  pure ALU: %d\n", aluPorts.size)
    printf("  ALU&BRU: %d\n", bruPorts.size)
    printf("  LSU: %d\n", lsuPorts.size)
    printf("  MDU: %d\n", completePorts.size)
    val portCount = aluPorts.size + bruPorts.size + lsuPorts.size + completePorts.size
    printf("  issue width: %d\n", portCount)
    for (p <- aluPorts)
      when(p.valid) {
        robState(p.robIdx).complete := True
        robState(p.robIdx).assignSomeByName(p.payload)
        for (j <- 0 until retireWidth) when(p.robIdx === popPtr + robInfo.popCount + j) {
          fifoIO.pop(j).payload.state.complete := True
          fifoIO.pop(j).payload.state.assignSomeByName(p.payload)
        }
      }
    for (p <- bruPorts)
      when(p.valid) {
        robState(p.robIdx).complete := True
        robState(p.robIdx).assignSomeByName(p.payload)
        for (j <- 0 until retireWidth) when(p.robIdx === popPtr + robInfo.popCount + j) {
          fifoIO.pop(j).payload.state.complete := True
          fifoIO.pop(j).payload.state.assignSomeByName(p.payload)
        }
      }
    for (p <- lsuPorts)
      when(p.valid) {
        robState(p.robIdx).complete := True
        robState(p.robIdx).assignSomeByName(p.payload)
        for (j <- 0 until retireWidth) when(p.robIdx === popPtr + robInfo.popCount + j) {
          fifoIO.pop(j).payload.state.complete := True
          fifoIO.pop(j).payload.state.assignSomeByName(p.payload)
        }
      }
    for (p <- completePorts)
      when(p.valid) {
        robState(p.payload).complete := True
        for (j <- 0 until retireWidth) when(p.payload === popPtr + robInfo.popCount + j) {
          fifoIO.pop(j).payload.state.complete := True
        }
      }
  }
}
