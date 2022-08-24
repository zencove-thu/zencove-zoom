package zencove.core

import zencove.builder._
import zencove.TLBConfig
import zencove.model.TLBEntry
import zencove.enum.TLBOp
import spinal.core._
import spinal.lib._
import zencove.MIPS32.ExceptionCode

case class TLBTranslateResult(
    isMatch: Bool,
    physAddr: UInt,
    cacheAttr: Bits,
    valid: Bool,
    dirty: Bool
) extends Nameable

class TLB(config: TLBConfig) extends Plugin[CPU] {
  case class EntryLoReg() extends Bundle {
    val G, V, D = Reg(Bool)
    val C = Reg(Bits(3 bits))
    val PFN = Reg(UInt(config.pfnWidth bits))
  }
  val regProbeFail = RegInit(False)
  val regIndex = Reg(UInt(config.indexWidth bits))
  val randomIndex = Reg(UInt(config.indexWidth bits))
  val wiredIndex = RegInit(U(0, config.indexWidth bits))
  val entryLoRegs = Vec(EntryLoReg(), 2)
  val regASID = Reg(Bits(config.asidWidth bits))
  val regVPN2 = Reg(UInt(config.vpnWidth - 1 bits))
  val pageMask =
    if (config.useMask) Reg(Bits(config.maskWidth bits)) else B(0, config.maskWidth bits)
  val machineCheck = False

  // Config resiter 1, 30~25, r, not modifiable, represent num of tlb entries - 1
  override def setup(pipeline: CPU): Unit = {
    val cp0 = pipeline.service[CP0Regs]
    import zencove.MIPS32._
    cp0.r(CP0.Index, 31 -> regProbeFail)
    cp0.rw(CP0.Index, 0 -> regIndex)
    // init max
    randomIndex.init(randomIndex.getAllTrue)
    cp0.r(CP0.Random, 0 -> randomIndex)
    cp0.rw(CP0.Wired, 0 -> wiredIndex)
    // 写Wired，Random重置为最大值
    cp0.onWrite(CP0.Wired)(randomIndex.setAll())
    def registerEntryLoFields(addr: Int, reg: EntryLoReg) {
      cp0.rw(addr, 0 -> reg.G, 1 -> reg.V, 2 -> reg.D, 3 -> reg.C, 6 -> reg.PFN)
    }
    registerEntryLoFields(CP0.EntryLo0, entryLoRegs(0))
    registerEntryLoFields(CP0.EntryLo1, entryLoRegs(1))
    cp0.rw(CP0.EntryHi, 0 -> regASID, 13 -> regVPN2)
    cp0.r(CP0.PageMask, 13 -> pageMask)
    if (config.useMask) cp0.w(CP0.PageMask, 13 -> pageMask)
  }

  // FIXME: TLB用啥boot state能保证不会写第一条就machine check呀
  private val entries = Vec(Reg(TLBEntry(config)), config.numEntries)
  private val asidMatches = entries.map({ entry => entry.hi.G || entry.hi.ASID === regASID })

  private def maskCompare(l: UInt, r: UInt, mask: Bits): Bool = {
    val newMask = ~mask.asUInt.resize(l.getBitsWidth)
    (l & newMask) === (r & newMask)
  }
  def translate(virtAddr: UInt, isStore: Bool) = new Area {
    private val results = for (i <- 0 until config.numEntries; entry = entries(i)) yield {
      val asidMatch = asidMatches(i)
      val vpn2Match =
        if (config.useMask)
          maskCompare(entry.hi.VPN2, virtAddr(13, config.vpnWidth - 1 bits), entry.mask)
        else entry.hi.VPN2 === virtAddr(13, config.vpnWidth - 1 bits)
      assert(config.evenOddBit >= 0, "dynamic page size is not supported")
      val lo = entry.lo(virtAddr(config.evenOddBit).asUInt)
      // val invalid = ~lo.V
      // val modified = isStore & lo.D
      val entryMatch = asidMatch && vpn2Match
      // 借用physAddr域存pfn
      TLBTranslateResult(entryMatch, lo.PFN, lo.C, entryMatch & lo.V, entryMatch & lo.D)
    }
    val isMatch = results.map(_.isMatch).orR
    val pfn = MuxOH(results.map(_.isMatch), results.map(_.physAddr))
    val physAddr = pfn @@ virtAddr(11 downto 0)
    val cacheAttr = MuxOH(results.map(_.isMatch), results.map(_.cacheAttr))
    val valid = results.map(_.valid).orR
    val dirty = results.map(_.dirty).orR
  }
  override def build(pipeline: CPU): Unit = {
    val tlbCommit = pipeline.service[TLBCommit]
    val tlbOp = tlbCommit.tlbOp

    // Random状态转移：向下循环
    when(tlbOp === TLBOp.WRITE_RANDOM) {
      randomIndex := Mux(randomIndex === wiredIndex, randomIndex.getAllTrue, randomIndex - 1)
    }

    // TLB指令
    val probeResults = for (i <- 0 until config.numEntries; entry = entries(i)) yield {
      val asidMatch = asidMatches(i)
      val vpn2Match =
        if (config.useMask) maskCompare(entry.hi.VPN2, regVPN2, entry.mask)
        else entry.hi.VPN2 === regVPN2
      val isMatch = asidMatch && vpn2Match
      (isMatch, U(i, config.indexWidth bits))
    }
    val probeHit = probeResults.map(_._1).orR
    val probeIndex = MuxOH(probeResults.map(_._1), probeResults.map(_._2))
    switch(tlbOp) {
      import TLBOp._
      is(PROBE) {
        regProbeFail := ~probeHit
        regIndex := probeIndex
      }
      is(READ) {
        val entry = entries(regIndex)
        if (config.useMask) pageMask := entry.mask
        regVPN2 := entry.hi.VPN2
        regASID := entry.hi.ASID
        for (i <- 0 until entryLoRegs.length; regEntryLo = entryLoRegs(i); lo = entry.lo(i)) {
          regEntryLo.PFN := lo.PFN
          regEntryLo.C := lo.C
          regEntryLo.D := lo.D
          regEntryLo.V := lo.V
          regEntryLo.G := entry.hi.G
        }
      }
      is(WRITE_INDEX, WRITE_RANDOM) {
        // 发生异常了也要写入，要不然怎么detect inconsistency
        val i = Mux(tlbOp === WRITE_INDEX, regIndex, randomIndex)
        // report Machine Check on probe hit
        // FIXME: 我对machine check有点疑惑，而且懒得处理machine check比中断优先级高的问题，就先不管它了
        // hint: machine check其实是异步异常
        // machineCheck setWhen (probeHit && probeIndex =/= i)
        val entry = entries(i)
        if (config.useMask) entry.mask := pageMask
        entry.hi.VPN2 := regVPN2
        entry.hi.ASID := regASID
        entry.hi.G := entryLoRegs.map(_.G).andR
        for (i <- 0 until entryLoRegs.length; regEntryLo = entryLoRegs(i); lo = entry.lo(i)) {
          lo.PFN := regEntryLo.PFN
          lo.C := regEntryLo.C
          lo.D := regEntryLo.D
          lo.V := regEntryLo.V
        }
      }
    }
  }
}
