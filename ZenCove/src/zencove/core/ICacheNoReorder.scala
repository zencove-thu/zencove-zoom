package zencove.core

import zencove.builder._
import zencove.ZencoveConfig
import spinal.core._
import zencove.model._
import spinal.lib._
import spinal.lib.bus.amba4.axi._
import spinal.lib.fsm._
import zencove.util._
import zencove.blackbox.mem.SDPRAM
import zencove.enum._

/** I-cache不采用reorder，直接平铺，在IF1选出words。
  *
  * @param config
  */
class ICacheNoReorder(config: ZencoveConfig) extends Plugin[FetchPipeline] with IBus {
  private val frontend = config.frontend
  private val icache = config.frontend.icache
  // valid有单index清空的可能，多写口
  val valids = Vec(Vec(RegInit(False), icache.ways), icache.sets)
  val dataRAMs =
    Seq.fill(icache.ways)(
      new SDPRAM(Vec(BWord(), icache.lineWords), icache.sets, false, useByteEnable = true)
    )
  val infoRAM = new SDPRAM(CacheLineInfo(icache), icache.sets, false)

  private object ICACHE_VALIDS extends Stageable(valids.dataType())
  private object ICACHE_RSPS extends Stageable(Vec(Vec(BWord(), frontend.fetchWidth), icache.ways))
  private object ICACHE_INFO extends Stageable(CacheLineInfo(icache))
  private object TAG_MATCHES extends Stageable(Bits(icache.ways bits))

  // iBus本身一定是read only，但对外可以转Axi4
  val iBus = Axi4ReadOnly(config.axi).setIdle()

  override def build(pipeline: FetchPipeline): Unit = pipeline plug new Area {
    import pipeline._
    // I-cache重取
    val doRefetch = False
    // IF1为气泡的时候跳过refetch，省一个周期
    val refetchValid = pipeline.IF1.arbitration.isValid && doRefetch
    // 读tag按照index找行
    val nextPC = pipeline.service[ProgramCounter].nextPC
    val rValid = IF1.arbitration.notStuck || refetchValid
    val rAddr = Mux(refetchValid, pipeline.IF1.input(PC), nextPC)
    val rPort = infoRAM.io.read
    rPort.cmd.valid := rValid
    rPort.cmd.payload := rAddr(icache.indexRange)
    val dataRs = Vec(dataRAMs.map(_.io.read))
    dataRs.foreach { p =>
      p.cmd.valid := rValid
      p.cmd.payload := rAddr(icache.indexRange)
    }
    IF1 plug new Area {
      import IF1._
      arbitration.haltItself setWhen refetchValid
      val pc = input(PC)
      // 读valid也插进流水线
      insert(ICACHE_VALIDS) := valids(pc(icache.indexRange))
      for (i <- 0 until icache.ways) {
        for (j <- 0 until frontend.fetchWidth) {
          insert(ICACHE_RSPS)(i)(j) := dataRAMs(i).io.read.rsp(pc(icache.wordOffsetRange) + j)
        }
      }
      insert(ICACHE_INFO) := rPort.rsp
      val cachePhysAddr = input(PC_PHYSICAL)
      for (i <- 0 until icache.ways) {
        insert(TAG_MATCHES)(i) := input(ICACHE_INFO).tags(i) === cachePhysAddr(icache.tagRange)
      }
    }
    IF2 plug new Area {
      import IF2._
      val reqValid = arbitration.isValidOnEntry && !input(EXCEPTION_OCCURRED)
      val reqCommit = reqValid && !arbitration.isStuck
      val virtPC = input(PC)
      val physPC = input(PC_PHYSICAL)
      // PC在cache line中的位置，用于计算fetch word是否有效
      val pcWordOffset = virtPC(icache.wordOffsetRange)
      val idx = virtPC(icache.indexRange)
      val tag = physPC(icache.tagRange) // physical tag
      val setValids = input(ICACHE_VALIDS)
      val wPort = infoRAM.io.write.setIdle()
      val dataWs = Vec(dataRAMs.map(_.io.write.setIdle()))
      val dataMasks = Vec(dataRAMs.map(_.io.writeMask.clearAll().subdivideIn(4 bits, true)))

      // cache查询
      val hits = setValids.zip(input(ICACHE_INFO).tags).map { case (valid, t) =>
        valid && t === tag
      }
      val hit = hits.orR
      val hitData = MuxOH(hits, input(ICACHE_RSPS))

      // 存储当前被填充的packet，避免IF2重取
      val storedPacket = Vec(Reg(BWord()), frontend.fetchWidth)

      when(reqCommit && hit) {
        // 命中，提交LRU修改
        assert(icache.ways == 2)
        val newInfo = input(ICACHE_INFO).copy()
        newInfo.tags := input(ICACHE_INFO).tags
        // hit 0, set 1
        newInfo.lru(0) := hits(0)
        wPort.valid.set()
        wPort.payload.address := idx
        wPort.payload.data := newInfo
      }

      // 解决cache miss
      val cacheRefillFSM = new StateMachine {
        val waitAXI = new State
        val readMem = new State
        val commit = new State
        val finish = new State
        disableAutoStart()
        setEntry(stateBoot)
        val rspId = Counter(icache.lineWords)
        // 反正寄存也不增加周期，还改善时序
        val refillValid = RegNext(iBus.r.fire)
        val refillWord = RegNext(iBus.r.payload.data)
        // 根据LRU选择一路
        val replaceWay = input(ICACHE_INFO).lru.asUInt

        // 填充stored packet
        for (i <- 0 until frontend.fetchWidth) {
          when(refillValid && rspId === pcWordOffset + i) {
            storedPacket(i) := refillWord
          }
        }

        stateBoot.whenIsActive {
          when(reqValid && !hit) {
            arbitration.haltItself.set()
            goto(waitAXI)
          }
        }

        waitAXI.whenIsActive {
          arbitration.haltItself.set()
          val ar = iBus.ar
          ar.payload.id := 0
          // 抹去offset
          ar.payload.addr := physPC(31 downto icache.offsetWidth) @@ U(0, icache.offsetWidth bits)
          ar.payload.len := icache.lineWords - 1 // burst len
          ar.payload.size := 2 // burst size = 4Bytes = 32 bits
          ar.payload.burst := 1 // burst type = INCR
          ar.payload.lock := 0 // normal access
          ar.payload.cache := 0 // device non-bufferable
          if (config.axi.useQos) ar.payload.qos := 0 // no QoS scheme
          ar.payload.prot := 0 // secure and normal(non-priviledged)
          ar.valid := True
          when(ar.ready) {
            rspId.clear()
            goto(readMem)
          }
        }

        readMem.whenIsActive {
          arbitration.haltItself.set()
          val r = iBus.r
          r.ready.set()
          when(refillValid) {
            dataWs(replaceWay).valid := True
            dataWs(replaceWay).payload.address := idx
            dataWs(replaceWay).payload.data.foreach(_ := refillWord)
            dataMasks(replaceWay)(rspId).setAll()
            rspId.increment()
          }
          when(r.valid && r.payload.last)(goto(commit))
        }

        commit.whenIsActive {
          arbitration.haltItself.set()
          // 对data写入最后一个word
          dataWs(replaceWay).valid := True
          dataWs(replaceWay).payload.address := idx
          dataWs(replaceWay).payload.data.foreach(_ := refillWord)
          dataMasks(replaceWay)(rspId).setAll()
          // 更新info
          assert(icache.ways == 2)
          val newInfo = input(ICACHE_INFO).copy()
          // 其它路tag保持
          newInfo.tags := input(ICACHE_INFO).tags
          // 选择的一路写入新tag
          newInfo.tags(replaceWay) := tag
          // 更新LRU
          newInfo.lru := ~input(ICACHE_INFO).lru
          // 设置valid
          valids(idx)(replaceWay).set()
          // 写入Mem
          wPort.valid.set()
          wPort.payload.address := idx
          wPort.payload.data := newInfo
          goto(finish)
        }

        finish.whenIsActive {
          // 避免IF1中指令取不到新写的cache，重取
          // 每一条都从state boot开始
          when(arbitration.notStuck) {
            doRefetch := True
            goto(stateBoot)
          }
        }
      }
      val fetchPacket = insert(pipeline.signals.FETCH_PACKET)
      fetchPacket.pc := virtPC
      fetchPacket.except.valid := input(EXCEPTION_OCCURRED)
      fetchPacket.except.payload.code := input(EXCEPTION_CODE)
      fetchPacket.except.payload.isTLBRefill := input(IS_TLB_REFILL)
      for (i <- 0 until frontend.fetchWidth) {
        val fetchWord = fetchPacket.insts(i)
        // e.g. 32B = 8W, 6的时候仅0(6), 1(7)有效
        if (i == 0) fetchWord.valid := True
        else fetchWord.valid := pcWordOffset < icache.lineWords - i
        fetchWord.payload := Mux(hit, hitData(i), storedPacket(i))
      }
    }
    // 解决应该清空cache的条件（只需要设置valid）
    val commit = new Area {
      val cacheCommit = pipeline.globalService[CacheCommit]
      val targetICache = cacheCommit.cacheOp.payload.sel === CacheSel.I
      val operation = cacheCommit.cacheOp
      val cacheOp = operation.op
      val addr = operation.addr
      val way = addr(icache.tagOffset, log2Up(icache.ways) bits)
      val index = addr(icache.indexRange)
      val tag = addr(icache.tagRange)
      when(operation.valid && targetICache) {
        switch(cacheOp) {
          import CacheOp._
          is(INDEX_INVALIDATE) {
            valids(index)(way) := False
          }
          is(HIT_INVALIDATE) {
            // lazy implementation invalidates all ways
            valids(index).foreach(_ := False)
          }
        }
      }
    }
  }
}
