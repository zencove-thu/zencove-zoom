package zencove.core

import spinal.core._
import zencove.enum._

case class InstructionParser(val inst: Bits) extends Bundle {
  def opcode = inst(31 downto 26)
  def rs = inst(25 downto 21).asUInt
  def rt = inst(20 downto 16).asUInt
  def rd = inst(15 downto 11).asUInt
  def fd = inst(10 downto 6).asUInt
  def funct = inst(5 downto 0)
  def immediate = inst(15 downto 0).asUInt
  def sa = fd
  def signExtendImm = immediate.asSInt.resize(32 bits).asUInt
  def zeroExtendImm = immediate.resize(32 bits)
  def instrIndex = inst(25 downto 0).asUInt
  def branchOffset = (inst(15 downto 0).asSInt @@ U(0, 2 bits)).resize(32 bits).asUInt
  def jumpTarget = instrIndex @@ U(0, 2 bits)
  def sel = inst(2 downto 0).asUInt
  def cp0Addr = rd @@ sel
  def code = inst(25 downto 6)
  def cacheSel = {
    val ret = CacheSel()
    ret.assignFromBits(inst(17 downto 16))
    ret
  }
  def cacheOp = {
    val ret = CacheOp()
    ret.assignFromBits(inst(20 downto 18))
    ret
  }
}
