package zencove.core

import zencove.builder._
import spinal.core._
import zencove.ZencoveConfig

class ConfigRegisters(config: ZencoveConfig) extends Plugin[CPU] {
  //PRId register, 15,0
  val companyOptions = U(config.PRId.companyOptions, 8 bits)
  val companyID = U(config.PRId.companyID, 8 bits)
  val processorID = U(config.PRId.processorID, 8 bits)
  val revision = U(config.PRId.revision, 8 bits)
  //all 4 fields are read-only, not modifiable.

  //Config register 0, 16, 0
  val M = Bool(config.maxConfigReg > 0) //Config 1 implemented
  val K23 = U(0, 3 bits)
  val KU = U(0, 3 bits) //These 2 fields are only settable when MMU is FIXED MAPPING
  val BE = Bool(config.config0.bigEndian) //big endian or little endian
  val AT = U(config.config0.architecture, 2 bits) //architecture type, currently MIPS32
  val AR = U(config.config0.revision, 3 bits) //revision number, currently 1
  val MT = U(config.config0.MMUType, 3 bits) //MMU type, currently TLB
  val VI = Bool(config.config0.virtualICache) //is ICache virtual, currently NOT
  val K0 = RegInit(U(3, 3 bits)) //Kseg0 cacheability
  def kseg0Cached = K0(0)

  override def setup(pipeline: CPU): Unit = {
    import zencove.MIPS32._
    val cp0 = pipeline.service(classOf[CP0Regs])
    cp0.r(CP0.PRId, 24 -> companyOptions)
    cp0.r(CP0.PRId, 16 -> companyID)
    cp0.r(CP0.PRId, 8 -> processorID)
    cp0.r(CP0.PRId, 0 -> revision)

    cp0.r(CP0.Config, 31 -> M)
    cp0.r(CP0.Config, 28 -> K23)
    cp0.r(CP0.Config, 25 -> KU)
    cp0.r(CP0.Config, 15 -> BE)
    cp0.r(CP0.Config, 13 -> AT)
    cp0.r(CP0.Config, 10 -> AR)
    cp0.r(CP0.Config, 7 -> MT)
    cp0.r(CP0.Config, 3 -> VI)
    cp0.rw(CP0.Config, 0 -> K0)

    if (config.maxConfigReg >= 1) {
      // mmu size, config register 1
      // M - 31 - config2 present (because we need config3)
      cp0.r(
        CP0.Config1,
        31 -> Bool(config.maxConfigReg > 1),
        25 -> U(config.tlb.numEntries - 1, 6 bits)
      )
      val icache = config.frontend.icache
      if (icache.enable) {
        var ISValue = icache.indexWidth - 6
        if (ISValue < 0) ISValue += 8
        val IL = U(icache.offsetWidth - 1, 3 bits)
        val IA = U(icache.ways - 1, 3 bits)
        cp0.r(CP0.Config1, 22 -> U(ISValue, 3 bits), 19 -> IL, 16 -> IA)
      } else {
        cp0.r(CP0.Config1, 19 -> U(0, 3 bits))
      }
      if (config.dcache.enable) {
        var DSValue = config.dcache.indexWidth - 6
        if (DSValue < 0) DSValue += 8
        val DL = U(config.dcache.offsetWidth - 1, 3 bits)
        val DA = U(config.dcache.ways - 1, 3 bits)
        cp0.r(CP0.Config1, 13 -> U(DSValue, 3 bits), 10 -> DL, 7 -> DA)
      } else {
        cp0.r(CP0.Config1, 10 -> U(0, 3 bits))
      }
      // other required fields should all be zero
    }

    /* M - 31 - config3 present (for interrupt control)
    TL - 23:20 - no tertiary cache
    SL - 7:4 - no secondary cache
    other required fields not implemented - value don't care
     */
    config.maxConfigReg >= 2 generate cp0.r(
      CP0.Config2,
      31 -> Bool(config.maxConfigReg > 2),
      20 -> U(0, 4 bits),
      4 -> U(0, 4 bits)
    )

    /* M - 31 - config4 not implemented
    BPG - 30 - TLB page >256MB not implemented
    RXI - 12 - RIE and XIE not implemented in PageGrain
    SP - 4 - support small page
    other required fields should be zero
     */
    config.maxConfigReg >= 3 generate cp0.r(
      CP0.Config3,
      31 -> Bool(config.maxConfigReg > 3)
    )
    assert(config.maxConfigReg < 4)

    /* HSS - 29:26 - no GPR shadow sets */
    cp0.r(CP0.SRSCtl, 26 -> U(0, 4 bits))
  }
  override def build(pipeline: CPU): Unit = {}
}
