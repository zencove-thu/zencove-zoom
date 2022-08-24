package zencove.util.amba4.axistream

import spinal.core._
import spinal.lib._

case class Axi4StreamConfig(
    dataWidth: Int,
    idWidth: Int = -1,
    useId: Boolean = true,
    useLast: Boolean = true,
    useKeep: Boolean = true,
    useStrb: Boolean = true,
    destWidth: Int = -1,
    userWidth: Int = -1
) {
  def useDest = destWidth >= 0
  def useUser = userWidth >= 0

  if (useId)
    require(idWidth >= 0, "You need to set idWidth")

  def dataType = Bits(dataWidth bits)
  def idType = UInt(idWidth bits)
  def lenType = UInt(8 bits)
  def bytePerWord = dataWidth / 8
  def symbolRange = log2Up(bytePerWord) - 1 downto 0
}

case class Axi4Stream(config: Axi4StreamConfig) extends Bundle {
  val data = Bits(config.dataWidth bits)
  val strb = if (config.useStrb) Bits(config.bytePerWord bits) else null
  val keepMask = if (config.useKeep) Bits(config.bytePerWord bits).setPartialName("keep") else null
  val last = if (config.useLast) Bool() else null
  val id = if (config.useId) UInt(config.idWidth bits) else null
  val user = if (config.useUser) Bits(config.userWidth bits) else null
  val dest = if (config.useDest) Bits(config.destWidth bits) else null

  def setStrb(): Unit = if (config.useStrb) strb := (1 << widthOf(strb)) - 1
  def setStrb(bytesLane: Bits): Unit = if (config.useStrb) strb := bytesLane
}

object Axi4StreamSpecRenamer {
  def apply[T <: Bundle](that: T): T = {
    def doIt = {
      that.flatten.foreach((bt) => {
        bt.setName(bt.getName().replace("payload_", "t"))
        bt.setName(bt.getName().replace("valid", "tvalid"))
        bt.setName(bt.getName().replace("ready", "tready"))
        if (bt.getName().startsWith("io_")) bt.setName(bt.getName().replaceFirst("io_", ""))
      })
    }
    if (Component.current == that.component)
      that.component.addPrePopTask(() => { doIt })
    else
      doIt

    that
  }
}
