package zencove.core

import zencove.builder.Plugin
import spinal.core._
import zencove.ZencoveConfig
import zencove.MIPS32.ExceptionCode
import zencove.enum.PrivMode

class InstAddrTranslate(config: ZencoveConfig) extends Plugin[FetchPipeline] {
  val pcInvalid = False
  val tlbError = Bool
  val badVaddr = UInt(32 bits)
  override def setup(pipeline: FetchPipeline): Unit = {
    val iExec = pipeline.service[ExceptionMux[_]]
    iExec.addExceptionSource(
      pipeline.IF1,
      ExceptionCode.addrErrorLoadOrIF,
      pcInvalid,
      badVaddr,
      priority = 13
    )
    iExec.addExceptionSource(pipeline.IF1, ExceptionCode.tlbLoadOrIF, tlbError, badVaddr, 12)
  }
  override def build(pipeline: FetchPipeline): Unit = {
    import pipeline.IF1
    val tlb = pipeline.globalService[TLB]
    val excHandler = pipeline.globalService[StatusProvider]
    IF1 plug new Area {
      import IF1._
      // fetch packet不跨行，更不跨页
      val virtPC = input(PC)
      pcInvalid setWhen (input(PC)(0) || input(PC)(1))
      tlbError := False
      badVaddr := virtPC

      val physPC = insert(PC_PHYSICAL)
      val pcCached = insert(ADDRESS_CACHED)
      val pcTrans = tlb.translate(virtPC, False)
      import config.virtMem._
      val staticCheck = config.virtMem.staticAreaCheck(virtPC)
      val ERL = excHandler.statusERL
      val privMode = excHandler.privMode
      pcInvalid setWhen (privMode === PrivMode.USER && !staticCheck.userAccessible)
      insert(IS_TLB_REFILL) := False
      when(ERL && inKuseg(virtPC, privMode)) {
        // kuseg unmapped uncached case, identity mapping
        physPC := staticCheck.fixedMapAddr
        pcCached := False
      } elsewhen (staticCheck.inMappedArea) {
        insert(IS_TLB_REFILL) setWhen (!pcTrans.isMatch)
        when(!pcTrans.isMatch || !pcTrans.valid) { //Not match or invalid
          tlbError := True
        } //because this is read operation only, so dirty is ignored.
        physPC := pcTrans.physAddr
        // 3 - Cacheable, 2 - Uncached
        pcCached := pcTrans.cacheAttr(0)
      } otherwise {
        physPC := staticCheck.fixedMapAddr
        pcCached := staticCheck.inCachedArea
        when(inKseg0(virtPC)) { // kseg0 cacheability controlled by ConfigK0
          pcCached := pipeline.globalService[ConfigRegisters].kseg0Cached
        }
      }
    }
  }
}
