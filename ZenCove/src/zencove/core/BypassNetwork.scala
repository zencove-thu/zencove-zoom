package zencove.core

import spinal.core._
import spinal.lib._
import zencove.builder.Plugin
import scala.collection.mutable
import zencove.RegFileConfig
import zencove.model._
import zencove.util._

class BypassNetwork(config: RegFileConfig) extends Plugin[CPU] {
  val writePorts = mutable.ArrayBuffer[Flow[RegWrite]]()
  private val readPorts = mutable.ArrayBuffer[(UInt, Flow[Bits])]()
  def writePort = {
    val port = Flow(RegWrite(config.prfAddrWidth))
    writePorts += port
    port
  }
  def readPort(addr: UInt) = {
    val port = Flow(BWord()).setIdle()
    readPorts += (addr -> port)
    port
  }
  override def build(pipeline: CPU): Unit = {
    // 前传逻辑
    readPorts.foreach { r =>
      // p0永远不应该被写，它是r0的固定映射
      // 永远不应该出现写冲突，rename处理掉了写冲突
      val writeOH = writePorts.map { p => p.valid && p.payload.addr === r._1 }
      when(writeOH.orR) { r._2.push(MuxOH(writeOH, writePorts.map(_.payload.data))) }
    }
  }
}
