package zencove.core

import zencove.builder._
import zencove.enum._
import spinal.core._
import zencove.MIPS32.ExceptionCode
import zencove.ZencoveConfig

/** Address generation unit. 对load/store执行对齐和查询TLB。
  */
class AGU(config: ZencoveConfig) extends Plugin[MemPipeline] {
  val tlbMod = False
  val tlbStore = False
  val tlbLoad = False
  val loadAddrError = False
  val storeAddrError = False
  override def setup(pipeline: MemPipeline): Unit = {
    import zencove.MIPS32._
    // exception interface service
    val iExec = pipeline.service[ExceptionMux[_]]
    iExec.addExceptionSource(
      pipeline.MEM1,
      ExceptionCode.addrErrorStore,
      storeAddrError,
      priority = 7
    )
    iExec.addExceptionSource(
      pipeline.MEM1,
      ExceptionCode.addrErrorLoadOrIF,
      loadAddrError,
      priority = 7
    )
    iExec.addExceptionSource(pipeline.MEM1, ExceptionCode.tlbLoadOrIF, tlbLoad, 3)
    iExec.addExceptionSource(pipeline.MEM1, ExceptionCode.tlbStore, tlbStore, 3)
    iExec.addExceptionSource(pipeline.MEM1, ExceptionCode.tlbModification, tlbMod, 1)
  }
  override def build(pipeline: MemPipeline): Unit = pipeline.MEM1 plug new Area {
    val tlb = pipeline.globalService[TLB]
    val excHandler = pipeline.globalService[StatusProvider]
    import pipeline.MEM1._
    import pipeline.signals._
    val issSlot = input(ISSUE_SLOT)
    val uop = issSlot.uop
    val virtAddr = input(MEMORY_ADDRESS)
    val physAddr = insert(MEMORY_ADDRESS_PHYSICAL)
    val accessType = uop.lsType
    val isLoad = uop.isLoad
    val excAsLoad = !uop.isStore
    val isStore = uop.isStore
    val byteEnable = insert(MEMORY_BE).assignDontCare()
    // FIXME: memWData还有优化空间，不是BE的可以不管
    val memWData = output(MEMORY_WRITE_DATA).allowOverride
    val dataWord = input(MEMORY_WRITE_DATA)
    val misalign = False

    // byte enable调整为对齐访问的，但是addr后两位保留，load后处理好写一些
    // 因此addr后两位对于实际load/store要忽略掉
    switch(accessType) {
      import LoadStoreType._
      is(BYTE, BYTE_U) {
        byteEnable := virtAddr(1 downto 0).mux(
          0 -> B"4'b0001",
          1 -> B"4'b0010",
          2 -> B"4'b0100",
          3 -> B"4'b1000"
        )
        memWData := virtAddr(1 downto 0).muxList(Seq.tabulate(4) { i =>
          i -> (dataWord |<< (i * 8))
        })
      }
      is(HALF, HALF_U) {
        misalign.setWhen(virtAddr(0))
        byteEnable := Mux(virtAddr(1), B"4'b1100", B"4'b0011")
        memWData := Mux(virtAddr(1), dataWord |<< 16, dataWord)
      }
      is(WORD) {
        misalign.setWhen(virtAddr(1 downto 0) =/= 0)
        byteEnable := 0xf
        memWData := dataWord
      }
      is(LEFT) {
        byteEnable := virtAddr(1 downto 0).mux(
          0 -> B"4'b0001",
          1 -> B"4'b0011",
          2 -> B"4'b0111",
          3 -> B"4'b1111"
        )
        memWData := virtAddr(1 downto 0).muxList(Seq.tabulate(4) { i =>
          (3 - i) -> B(0, i * 8 bits) ## dataWord(31 downto (i * 8))
        })
      }
      is(RIGHT) {
        byteEnable := virtAddr(1 downto 0).mux(
          0 -> B"4'b1111",
          1 -> B"4'b1110",
          2 -> B"4'b1100",
          3 -> B"4'b1000"
        )
        memWData := virtAddr(1 downto 0).muxList(Seq.tabulate(4) { i =>
          i -> dataWord(0, (4 - i) * 8 bits) ## B(0, i * 8 bits)
        })
      }
    }
    // unaligned load用WDATA传rt的读结果，不可改变；aligned load不使用WDATA
    when(isLoad)(memWData := input(MEMORY_WRITE_DATA))

    // translate
    val memAddrTrans = tlb.translate(virtAddr, isStore)
    val addrCached = insert(ADDRESS_CACHED)
    import config.virtMem._
    val staticCheck = config.virtMem.staticAreaCheck(virtAddr)
    val ERL = excHandler.statusERL
    val privMode = excHandler.privMode
    insert(IS_TLB_REFILL) := False
    when(ERL && inKuseg(virtAddr, privMode)) {
      // kuseg unmapped uncached case, identity mapping
      physAddr := staticCheck.fixedMapAddr
      addrCached := False
    } elsewhen (staticCheck.inMappedArea) {
      insert(IS_TLB_REFILL) setWhen !memAddrTrans.isMatch
      when(!memAddrTrans.isMatch || !memAddrTrans.valid) { //tlb refill or invalid
        tlbStore setWhen isStore
        tlbLoad setWhen excAsLoad
      } elsewhen (isStore && !memAddrTrans.dirty) { //not dirty but written
        tlbMod := True
      }
      physAddr := memAddrTrans.physAddr
      // 3 - Cacheable, 2 - Uncached
      addrCached := memAddrTrans.cacheAttr(0)
    } otherwise {
      physAddr := staticCheck.fixedMapAddr
      addrCached := staticCheck.inCachedArea
      when(inKseg0(virtAddr)) { // kseg0 cacheability controlled by ConfigK0
        addrCached := pipeline.globalService[ConfigRegisters].kseg0Cached
      }
    }

    // exception
    val addrError = (privMode === PrivMode.USER && !staticCheck.userAccessible) || misalign
    loadAddrError setWhen (excAsLoad && addrError)
    storeAddrError setWhen (isStore && addrError)
  }
}
