package zencove.util

import spinal.core._
import scala.collection.mutable

trait CsrDataField {
  val that: Data
  val bitOffset: Int
}

trait CsrCallbackField {
  val doThat: () => Unit
}

case class CsrWrite(that: Data, bitOffset: Int) extends CsrDataField
case class CsrRead(that: Data, bitOffset: Int) extends CsrDataField
//Used for special cases, as MIP where there shadow stuff
case class CsrReadToWriteOverride(that: Data, bitOffset: Int) extends CsrDataField
case class CsrOnWrite(doThat: () => Unit) extends CsrCallbackField
case class CsrDuringWrite(doThat: () => Unit) extends CsrCallbackField
case class CsrDuringRead(doThat: () => Unit) extends CsrCallbackField
case class CsrDuring(doThat: () => Unit) extends CsrCallbackField
case class CsrOnRead(doThat: () => Unit) extends CsrCallbackField

trait CsrIfBase extends Area {
  val askWrite: Bool
  val askRead: Bool
  val doWrite: Bool
  val doRead: Bool

  val readDataInit: Bits
  val readData: Bits
  val writeData: Bits
  val readToWriteData: Bits
  // val readError: Bool

  val readAddress: UInt
  val writeAddress: UInt
}

/** 来自Vexriscv，注册CSR域，并自动生成读写逻辑。
  *
  * 实际上标准库已经有regif了，其优点是融入了自动doc生成，缺点是对象是自动创建的，灵活性不足。
  */
trait CsrAddressMapping extends CsrIfBase {
  protected val mapping = mutable.LinkedHashMap[Int, mutable.ArrayBuffer[Any]]()
  protected val always = mutable.ArrayBuffer[Any]()

  protected def addMappingAt(address: Int, that: Any) =
    mapping.getOrElseUpdate(address, new mutable.ArrayBuffer[Any]) += that

  // data field
  def r(csrAddress: Int, bitOffset: Int, that: Data): Unit =
    addMappingAt(csrAddress, CsrRead(that, bitOffset))
  def w(csrAddress: Int, bitOffset: Int, that: Data): Unit =
    addMappingAt(csrAddress, CsrWrite(that, bitOffset))
  def rw(csrAddress: Int, bitOffset: Int, that: Data): Unit = {
    r(csrAddress, bitOffset, that)
    w(csrAddress, bitOffset, that)
  }
  def r2w(csrAddress: Int, bitOffset: Int, that: Data): Unit =
    addMappingAt(csrAddress, CsrReadToWriteOverride(that, bitOffset))

  def rw(csrAddress: Int, thats: (Int, Data)*): Unit = for (that <- thats)
    rw(csrAddress, that._1, that._2)
  def w(csrAddress: Int, thats: (Int, Data)*): Unit = for (that <- thats)
    w(csrAddress, that._1, that._2)
  def r(csrAddress: Int, thats: (Int, Data)*): Unit = for (that <- thats)
    r(csrAddress, that._1, that._2)
  def rw[T <: Data](csrAddress: Int, that: T): Unit = rw(csrAddress, 0, that)
  def w[T <: Data](csrAddress: Int, that: T): Unit = w(csrAddress, 0, that)
  def r[T <: Data](csrAddress: Int, that: T): Unit = r(csrAddress, 0, that)

  // callback
  def onWrite(csrAddress: Int)(body: => Unit): Unit =
    addMappingAt(csrAddress, CsrOnWrite(() => body))
  def duringWrite(csrAddress: Int)(body: => Unit): Unit =
    addMappingAt(csrAddress, CsrDuringWrite(() => body))
  def duringRead(csrAddress: Int)(body: => Unit): Unit =
    addMappingAt(csrAddress, CsrDuringRead(() => body))
  def during(csrAddress: Int)(body: => Unit): Unit =
    addMappingAt(csrAddress, CsrDuring(() => body))
  def onRead(csrAddress: Int)(body: => Unit): Unit =
    addMappingAt(csrAddress, CsrOnRead(() => { body }))
  // def duringAny(): Bool = ???
  def duringAnyRead(body: => Unit): Unit =
    always += CsrDuringRead(() => body)
  def duringAnyWrite(body: => Unit): Unit =
    always += CsrDuringWrite(() => body)
  def onAnyRead(body: => Unit): Unit = always += CsrOnRead(() => body)
  def onAnyWrite(body: => Unit): Unit =
    always += CsrOnWrite(() => body)

  def isWriting(csrAddress: Int): Bool = {
    val ret = False
    onWrite(csrAddress) {
      ret := True
    }
    ret
  }
  def isReading(csrAddress: Int): Bool = {
    val ret = False
    onRead(csrAddress) {
      ret := True
    }
    ret
  }

  // logic generation
  private def doReadJobs(jobs: mutable.ArrayBuffer[Any]): Unit = {
    val withRead =
      jobs.exists(j => j.isInstanceOf[CsrRead] || j.isInstanceOf[CsrOnRead])
    for (element <- jobs) element match {
      case element: CsrDuringRead => when(askRead)(element.doThat())
      case element: CsrDuring     => element.doThat()
      case element: CsrOnRead     => when(doRead)(element.doThat())
      case element: CsrRead if element.that.getBitsWidth != 0 =>
        readDataInit(
          element.bitOffset,
          element.that.getBitsWidth bits
        ) := element.that.asBits
      case _ =>
    }
  }
  private def doWriteJobs(jobs: mutable.ArrayBuffer[Any]): Unit = {
    val withWrite = jobs.exists(j =>
      j.isInstanceOf[CsrWrite] ||
        j.isInstanceOf[CsrOnWrite] ||
        j.isInstanceOf[CsrDuringWrite]
    )
    for (element <- jobs) element match {
      case element: CsrDuringWrite => when(askWrite)(element.doThat())
      case element: CsrDuring      => element.doThat()
      case element: CsrWrite =>
        when(doWrite)(
          element.that.assignFromBits(
            writeData(
              element.bitOffset,
              element.that.getBitsWidth bits
            )
          )
        )
      case element: CsrOnWrite => when(doWrite)(element.doThat())
      case _                   =>
    }
  }
  private def doJobsOverride(jobs: mutable.ArrayBuffer[Any]): Unit = {
    for (element <- jobs) element match {
      case element: CsrReadToWriteOverride if element.that.getBitsWidth != 0 =>
        readToWriteData(
          element.bitOffset,
          element.that.getBitsWidth bits
        ) := element.that.asBits
      case _ =>
    }
  }

  protected def genRead() = switch(readAddress) {
    for ((address, jobs) <- mapping) {
      is(address) {
        doReadJobs(jobs)
      }
    }
  }
  protected def genWrite() = switch(writeAddress) {
    for ((address, jobs) <- mapping) {
      is(address) {
        doWriteJobs(jobs)
      }
    }
  }
  protected def genOverride() = switch(writeAddress) {
    for (
      (address, jobs) <- mapping
      if jobs.exists(_.isInstanceOf[CsrReadToWriteOverride])
    ) {
      is(address) {
        doJobsOverride(jobs)
      }
    }
  }
  protected def genAlways() = always.foreach {
    case element: CsrDuringWrite => when(askWrite) { element.doThat() }
    case element: CsrDuringRead  => when(askRead) { element.doThat() }
    case element: CsrOnWrite     => when(doWrite) { element.doThat() }
    case element: CsrOnRead      => when(doRead) { element.doThat() }
  }
}
