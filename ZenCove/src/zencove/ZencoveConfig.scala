package zencove

import spinal.core._
import spinal.lib._
import spinal.lib.bus.wishbone.WishboneConfig
import spinal.lib.bus.amba4.axi.Axi4Config
import spinal.lib.logic.Masked
import zencove.enum.PrivMode

final case class TLBConfig(
    useMask: Boolean = false,
    numEntries: Int = 8,
    physAddrWidth: Int = 32,
    evenOddBit: Int = 12 // 4KB page，设成-1来表示可以动态改变的页大小
) {
  val indexWidth = log2Up(numEntries)
  val virtAddrWidth = 32
  val offsetWidth = 12
  val asidWidth = 8
  val maskWidth = 16
  val vpnWidth = virtAddrWidth - offsetWidth
  val pfnWidth = physAddrWidth - offsetWidth
}

class MemorySegment(
    base: Long,
    width: Int
) {
  val mask = Masked(base, (1L << 32) - (1L << width))
}

final case class VirtMemSegment(
    name: String,
    base: Long,
    width: Int,
    mapped: Boolean,
    cached: Boolean = true,
    fixedMapBase: Long = -1
) extends MemorySegment(base, width) {
  assert(mapped || fixedMapBase >= 0)
  def privPrefix = name(0)
  def userAccessible = privPrefix == 'u'
}

/** Virtual memory config.
  */
final case class VirtMemConfig(
    memSegs: Seq[VirtMemSegment] = Seq(
      VirtMemSegment("kseg3", 0xe0000000L, 29, true),
      VirtMemSegment("sseg", 0xc0000000L, 29, true),
      VirtMemSegment("kseg1", 0xa0000000L, 29, false, false, fixedMapBase = 0L),
      // Fix for performance test, unmapped cached 1MB area
      VirtMemSegment("kseg.5", 0x9fc00000L, 20, false, fixedMapBase = 0x1fc00000L),
      // Fix for speculative load in this area, upper 256MB not included in kseg0
      VirtMemSegment("kseg0", 0x80000000L, 28, false, fixedMapBase = 0L),
      VirtMemSegment("useg", 0L, 31, true, fixedMapBase = 0L)
    )
) {
  // use composite to give proper name
  def staticAreaCheck(virtAddr: UInt) = new Composite(virtAddr) {
    private val segWithMatch = memSegs.map { seg => (seg, seg.mask === virtAddr.asBits) }
    val inMappedArea = segWithMatch.filter(_._1.mapped).map(_._2).orR
    val inCachedArea = segWithMatch.filter(_._1.cached).map(_._2).orR
    val userAccessible = segWithMatch.filter(_._1.userAccessible).map(_._2).orR
    val fixedMapAddr = virtAddr.clone()
    fixedMapAddr := virtAddr
    segWithMatch.filter(!_._1.mapped) foreach { case (seg, isMatch) =>
      when(isMatch) {
        fixedMapAddr(fixedMapAddr.high downto seg.width) := (seg.fixedMapBase >> seg.width)
      }
    }
  }
  def inKuseg(virtAddr: UInt, privMode: SpinalEnumCraft[PrivMode.type]) = new Composite(virtAddr) {
    private val result = memSegs.find(_.name == "useg")
    val inKuseg = result match {
      case Some(useg) => useg.mask === virtAddr.asBits && privMode === PrivMode.KERNEL
      case None       => False
    }
  }.inKuseg
  def inKseg0(virtAddr: UInt) = new Composite(virtAddr) {
    private val result = memSegs.find(_.name == "kseg0")
    val inKseg0 = result match {
      case Some(seg) => seg.mask === virtAddr.asBits
      case None      => False
    }
  }.inKseg0
}

final case class PRIdConfig(
    companyOptions: Int = 0,
    companyID: Int = 1,
    processorID: Int = 0x80, // default 4Kc
    revision: Int = 0
)

final case class ConfigRegister0(
    fixedMapping: Boolean = false,
    bigEndian: Boolean = false,
    architecture: Int = 0, //0 for MIPS32
    revision: Int = 0, //0 for release 1
    MMUType: Int = 1, //1 for standard TLB
    virtualICache: Boolean = false //is ICache VIVT
)

final case class InterruptConfig(
    withTimerInt: Boolean = false,
    timerIntIP: Int = 7
)

abstract class CacheBasicConfig {
  val sets: Int
  val lineSize: Int
  val ways: Int
  val offsetWidth = log2Up(lineSize)
  val offsetRange = (offsetWidth - 1) downto 0
  val wordOffsetRange = (offsetWidth - 1) downto 2
  val indexWidth = log2Up(sets)
  val indexRange = (offsetWidth + indexWidth - 1) downto offsetWidth
  val tagOffset = offsetWidth + indexWidth
  val tagRange = 31 downto tagOffset
  def wordCount = sets * lineSize / 4
  def lineWords = lineSize / 4
}

final case class ICacheConfig(
    sets: Int = 128,
    lineSize: Int = 32,
    ways: Int = 2,
    useReorder: Boolean = false
) extends CacheBasicConfig {
  require(sets * lineSize <= (4 << 10), "4KB is VIPT limit")
  val enable = true
}
final case class DCacheConfig(
    sets: Int = 64,
    lineSize: Int = 64,
    ways: Int = 2,
    waitForB: Boolean = true
) extends CacheBasicConfig {
  require(sets * lineSize <= (4 << 10), "4KB is VIPT limit")
  val enable = true
}

// 4KB BTB
final case class BTBConfig(
    sets: Int = 1024,
    lineSize: Int = 4,
    ways: Int = 1,
    rasEntries: Int = 8
) extends CacheBasicConfig {
  val enable = true
}

final case class BPUConfig(
    sets: Int = 1024,
    phtSets: Int = 8192,
    historyWidth: Int = 5,
    counterWidth: Int = 2
) {
  val indexWidth = log2Up(sets)
  val indexRange = 2 until 2 + indexWidth
  val ways = 1 << historyWidth
  val phtIndexWidth = log2Up(phtSets)
  val phtPCRange = 2 until 2 + phtIndexWidth - historyWidth
  val counterType = HardType(UInt(counterWidth bits))
  val historyType = HardType(UInt(historyWidth bits))
  val useGlobal = true
  val useLocal = false
  val useHybrid = false
}

final case class FrontendConfig(
    pcInit: Long = 0xbfc00000L,
    icache: ICacheConfig = ICacheConfig(),
    btb: BTBConfig = BTBConfig(),
    bpu: BPUConfig = BPUConfig(),
    fetchWidth: Int = 4,
    fetchBufferDepth: Int = 8
)

final case class DecodeConfig(
    decodeWidth: Int = 3,
    allUnique: Boolean = false
)

final case class RegFileConfig(
    nArchRegs: Int = 32,
    nPhysRegs: Int = 31 + 32
) {
  val arfAddrWidth = log2Up(nArchRegs)
  val prfAddrWidth = log2Up(nPhysRegs)
  val rPortsEachInst = 2
}

final case class ROBConfig(
    robDepth: Int = 32,
    retireWidth: Int = 3
) {
  val robAddressWidth = log2Up(robDepth)
}

abstract class IssueConfig {
  val issueWidth: Int
  val depth: Int
  val alterImpl: Boolean // alternative implementaion, influences timing
  val addrWidth = log2Up(depth)
}

final case class HiLoConfig(
    nPhysRegs: Int = 4
) {
  val prfAddrWidth = log2Up(nPhysRegs)
}

final case class IntIssueConfig(
    issueWidth: Int = 2,
    bruIdx: Int = 0,
    cp0Idx: Int = 0,
    depth: Int = 7,
    alterImpl: Boolean = true
) extends IssueConfig {
  require(0 <= bruIdx && bruIdx < issueWidth)
  require(0 <= cp0Idx && cp0Idx < issueWidth)
}

final case class MemIssueConfig(
    depth: Int = 5,
    alterImpl: Boolean = true
) extends IssueConfig {
  val issueWidth: Int = 1
}

final case class MulDivConfig(
    depth: Int = 3,
    multiplyLatency: Int = 2,
    blackboxDivider: Boolean = false,
    divisionEarlyOutWidth: Int = 16, // set to 0 to disable early out
    alterImpl: Boolean = true
) extends IssueConfig {
  val issueWidth: Int = 1
  def useDivisionEarlyOut = divisionEarlyOutWidth > 0
}

final case class ISAConfig(
    // MIPS II (mips2)
    useTrap: Boolean = false,
    useSYNC: Boolean = false,
    useBranchLikely: Boolean = false,
    // MIPS III (mips3)
    // MIPS IV (mips4)
    usePREF: Boolean = false,
    useCondMove: Boolean = false,
    // MIPS32r1
    useCLO: Boolean = false,
    useWAIT: Boolean = false,
    useMUL: Boolean = false,
    useMADD: Boolean = false
)

object ISAConfig {
  def mips1 = ISAConfig()
  def mips2 = ISAConfig(
    useTrap = true,
    useSYNC = true
  )
  def mips3 = mips2
  def mips4 = ISAConfig(
    useTrap = true,
    useSYNC = true,
    usePREF = true,
    useCondMove = true
  )
  def mips32 = ISAConfig(
    useTrap = true,
    useSYNC = true,
    usePREF = true,
    useCondMove = true,
    useCLO = true,
    useWAIT = true,
    useMUL = true,
    useMADD = true
  )
}

final case class ZencoveConfig(
    axi: Axi4Config = Axi4Config(addressWidth = 32, dataWidth = 32, idWidth = 5, useRegion = false),
    frontend: FrontendConfig = FrontendConfig(),
    decode: DecodeConfig = DecodeConfig(),
    regFile: RegFileConfig = RegFileConfig(),
    hlu: HiLoConfig = HiLoConfig(),
    rob: ROBConfig = ROBConfig(),
    intIssue: IntIssueConfig = IntIssueConfig(),
    memIssue: MemIssueConfig = MemIssueConfig(),
    mulDiv: MulDivConfig = MulDivConfig(),
    dcache: DCacheConfig = DCacheConfig(),
    tlb: TLBConfig = TLBConfig(),
    virtMem: VirtMemConfig = VirtMemConfig(),
    PRId: PRIdConfig = PRIdConfig(),
    maxConfigReg: Int = 2,
    config0: ConfigRegister0 = ConfigRegister0(),
    interrupt: InterruptConfig = InterruptConfig(),
    storeBufferDepth: Int = 8,
    storeBufferAlterImpl: Boolean = true,
    isa: ISAConfig = ISAConfig(),
    useSpeculativeWakeup: Boolean = true,
    useUdBusBuffer: Boolean = true,
    debug: Boolean = false,
    pcWidth: Int = 30
)

object ZencoveConfig {

  /** Wide is intended to trade frequency for count.
    *
    * @return
    */
  def wide = ZencoveConfig(
    intIssue = IntIssueConfig(issueWidth = 3)
  )

  /** Normal is used for performance test.
    *
    * @return
    */
  def normal = ZencoveConfig()

  /** Small is intended to trade count for frequency.
    *
    * @return
    */
  def small = ZencoveConfig(
    frontend = FrontendConfig(btb = BTBConfig(rasEntries = 8), fetchBufferDepth = 8),
    hlu = HiLoConfig(nPhysRegs = 4),
    intIssue = IntIssueConfig(depth = 6),
    memIssue = MemIssueConfig(depth = 4),
    tlb = TLBConfig(numEntries = 4),
    storeBufferDepth = 6
  )

  def smaller = ZencoveConfig(
    regFile = RegFileConfig(nPhysRegs = 32 + 16),
    intIssue = IntIssueConfig(issueWidth = 2, depth = 6),
    memIssue = MemIssueConfig(depth = 4),
    storeBufferDepth = 6,
    decode = DecodeConfig(decodeWidth = 2),
    rob = ROBConfig(retireWidth = 2)
  )

  /** Tiny is used for quick synthesis, e.g. ILA.
    *
    * @return
    */
  def tiny = ZencoveConfig(
    frontend = FrontendConfig(btb = BTBConfig(rasEntries = 4), fetchBufferDepth = 8),
    regFile = RegFileConfig(nPhysRegs = 32 + 16),
    rob = ROBConfig(robDepth = 16),
    hlu = HiLoConfig(nPhysRegs = 4),
    intIssue = IntIssueConfig(issueWidth = 2, depth = 6),
    memIssue = MemIssueConfig(depth = 4),
    mulDiv = MulDivConfig(depth = 3),
    storeBufferDepth = 5
  )
}
