package zencove.core

import zencove.builder._
import spinal.core._
import spinal.lib._
import scala.collection.mutable
import zencove.MIPS32
import zencove.enum.RegWriteAddr
import zencove.util._

class CP0Regs extends Plugin[CPU] with CsrAddressMapping {
  val readAddress: UInt = UInt(8 bits)
  val writeAddress: UInt = UInt(8 bits)
  val readDataInit = BWord(0)
  val readData = CombInit(readDataInit)
  val writeData: Bits = BWord()

  val readToWriteData: Bits = CombInit(readData)
  val askRead: Bool = False
  val askWrite: Bool = Bool
  val doWrite: Bool = CombInit(askWrite)
  val doRead: Bool = CombInit(askRead)
  val cp0RAddr = readAddress
  val isReadInst = askRead
  val readCommit = doRead

  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    // write logic
    val cp0Commit = pipeline.service[CP0Commit]
    val cp0Write = cp0Commit.cp0Write
    // FIXME: 如果read产生副作用，那么read需要在commit做提交
    writeAddress := cp0Write.payload.addr
    writeData := cp0Write.payload.data
    askWrite := cp0Write.valid
    // Translation of the csr mapping into real logic
    Component.current.afterElaboration {
      genRead()
      genWrite()
      genOverride()
      genAlways()
    }
  }
}
