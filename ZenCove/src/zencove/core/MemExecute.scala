package zencove.core

import zencove.builder._
import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.model._
import zencove.enum._
import zencove.util._

class MemExecute(config: ZencoveConfig) extends Plugin[MemPipeline] {
  private val dcache = config.dcache
  private val iqDepth = config.memIssue.depth
  private val rPorts = config.regFile.rPortsEachInst
  private val prfAddrWidth = config.regFile.prfAddrWidth
  val rrdReq = Vec(UInt(prfAddrWidth bits), rPorts)
  var rrdRsp: Vec[Bits] = null
  var clrBusy: Flow[UInt] = null
  var wPort: Flow[RegWrite] = null
  var robWrite: Flow[ROBStateLSUPort] = null
  override def setup(pipeline: MemPipeline): Unit = {
    val PRF = pipeline.globalService[PhysRegFile]
    val IQ = pipeline.globalService[MemIssueQueue]
    val ROB = pipeline.globalService[ROBFIFO]
    rrdRsp = Vec(rrdReq.map(PRF.readPort(_)))
    clrBusy = PRF.clearBusy
    wPort = PRF.writePort(true)
    robWrite = ROB.lsuPort
  }
  override def build(pipeline: MemPipeline): Unit = {
    import pipeline.signals._
    val flush = pipeline.globalService[CommitFlush].regFlush
    pipeline plug new Area {
      // clear memory pipeline
      pipeline.stages.drop(1).foreach { s =>
        s.arbitration.removeIt setWhen flush
      }
    }
    pipeline.ISS plug new Area {
      import pipeline.ISS._
      val storeBuffer = pipeline.service[StoreBuffer]
      val IQ = pipeline.globalService[MemIssueQueue]
      // RRD, MEM1, MEM2都是潜在的push者，要保证所有发射的都能push进去
      // programmed full, 不是真的满但也不能发射了
      val stBufProgFull = storeBuffer.queue(storeBuffer.depth - 4).valid
      // CACHE指令不必顺序执行，ROB会将其重排序retire
      val storeMask = IQ.queue.map { slot =>
        slot.payload.uop.isStore
      }
      require(rPorts == 2)
      for (i <- 0 to 0) {
        val slot = IQ.queue(i).payload
        val waken = (0 until rPorts).map { j => slot.rRegs(j).valid }.andR
        // 0可以使store唤醒，但要保证store buffer一定有空间不会stuck，否则不能唤醒store，且任何一个store都会阻拦后面的load提交
        // note: 如果队列很深，那么唤醒还是需要写到寄存器里
        // val storeBarrier =
        //   if (i == 0) !(storeMask(0) && stBufProgFull) else !storeMask.take(i + 1).orR
        // fully ordered load&store
        // 考虑到uncached load也要进store buffer，因此所有uop都要保证store buffer不会满
        // 但是store buffer占用率没有太大变化，因此对load影响不会太大
        val storeBarrier = if (i == 0) !stBufProgFull else False
        // 计算每个槽是否valid
        IQ.issueReq := IQ.queue(i).valid && waken && storeBarrier
      }
      IQ.issueFire clearWhen arbitration.isStuck
      // one-hot插入issue slot，并设置valid
      val issSlot = insert(ISSUE_SLOT)
      issSlot := IQ.queue(0).payload
      val issValid = IQ.issueReq
      // load address / cache operation / store address
      val issueLoad = issValid && IQ.queue(0).uop.isLoad
      val issueStore = issValid && IQ.queue(0).uop.isStore

      // 发射STD逻辑
      val stBufPop = storeBuffer.queueIO.popPort
      // Cache, load, std 三个人抢cache，所以std要不然在空的时候发射，要不然sta发射的时候跟着发射
      val stdValid = stBufPop.valid && stBufPop.retired && (issueStore || !issValid)
      stBufPop.ready setWhen (!arbitration.isStuck && stdValid)
      insert(STD_SLOT).valid := stdValid
      insert(STD_SLOT).payload := stBufPop.payload

      // 仅load/STA与arbitration相关
      arbitration.removeIt setWhen (!arbitration.isStuck && !issValid)
      arbitration.removeIt setWhen (flush && !arbitration.isStuck)
    }
    pipeline.RRD plug new Area {
      import pipeline.RRD._
      val issSlot = input(ISSUE_SLOT)
      for (i <- 0 until rPorts) {
        rrdReq(i) := issSlot.rRegs(i).payload
        insert(REG_READ_RSP)(i) := rrdRsp(i)
      }
      val addrOffset = issSlot.uop.immField.asSInt.resize(32 bits).asUInt
      insert(MEMORY_ADDRESS) := input(REG_READ_RSP)(0).asUInt + addrOffset
      insert(MEMORY_WRITE_DATA) := input(REG_READ_RSP)(1)

    }
    pipeline.MEM1 plug new Area {
      import pipeline.MEM1._
      val issSlot = input(ISSUE_SLOT)
      val std = input(STD_SLOT)
      val isLDU = std.valid && !std.isStore
      val wRegValid = Mux(std.valid, std.payload.wReg.valid, issSlot.uop.doRegWrite)
      val wRegPayload = Mux(std.valid, std.payload.wReg.payload, issSlot.wReg)
      if (config.useSpeculativeWakeup) {
        // 推测唤醒，一个气泡
        // 若推测失败（MEM2非hit），则暂停所有其余流水的RRD
        clrBusy.valid := ((arbitration.isValid && input(ADDRESS_CACHED)) || isLDU) &&
          arbitration.notStuck && wRegValid
        clrBusy.payload := wRegPayload
      }
    }
    pipeline.MEM2 plug new Area {
      import pipeline.MEM2._
      val std = input(STD_SLOT)
      val isLDU = std.valid && !std.isStore
      if (config.useSpeculativeWakeup) {
        pipeline.globalService[SpeculativeWakeupHandler].wakeupFailed setWhen (
          arbitration.isStuck && output(WRITE_REG).valid
        )
      } else {
        // 唤醒，2个气泡
        // 初次执行时若uncached则不能唤醒，因为数据尚未准备好
        clrBusy.valid := ((arbitration.isValid && input(ADDRESS_CACHED)) || isLDU) &&
          arbitration.notStuck && output(WRITE_REG).valid
        clrBusy.payload := output(WRITE_REG).payload
      }
      when(!input(EXCEPTION_OCCURRED)) {
        // 用于CACHE指令复用badVA填物理地址
        output(MEMORY_ADDRESS) := input(MEMORY_ADDRESS_PHYSICAL)
      }
      when(isLDU) {
        output(WRITE_REG) := std.wReg
      }
    }
    pipeline.WB plug new Area {
      import pipeline.WB._
      val std = input(STD_SLOT)
      val isLDU = std.valid && !std.isStore
      wPort.valid := ((arbitration.isValid && input(ADDRESS_CACHED)) || isLDU) &&
        arbitration.notStuck && input(WRITE_REG).valid
      wPort.addr := input(WRITE_REG).payload
      wPort.data := input(MEMORY_READ_DATA)

      // FIXME: 9829e0c8将wakeup改在了WB，但是为什么？ - 若tag compare在MEM2，则这个stuck的path会很长
      // 初次执行时若uncached则不能唤醒，因为数据尚未准备好
      // clrBusy.valid := wPort.valid
      // clrBusy.payload := wPort.addr
      val ROB = pipeline.globalService[ROBFIFO]
      robWrite.valid := arbitration.isValidNotStuck
      robWrite.robIdx := input(ROB_IDX)
      robWrite.except.valid := input(EXCEPTION_OCCURRED)
      robWrite.except.payload.code := input(EXCEPTION_CODE)
      robWrite.except.payload.badVA := input(MEMORY_ADDRESS)
      robWrite.except.payload.isTLBRefill := input(IS_TLB_REFILL)
      robWrite.lsuUncached := !input(ADDRESS_CACHED)
      // robWrite.intResult := wPort.data.asUInt
    }
    for (stageIndex <- 0 until pipeline.stages.length) {
      val stage = pipeline.stages(stageIndex)
      // stuck的时候使得next stage采样到STD不valid，非stuck的时候STD才能被next stage采样
      stage.output(STD_SLOT).valid clearWhen stage.arbitration.isStuck
    }
    Component.current.afterElaboration {
      pipeline.stages.drop(1).foreach { stage =>
        stage.input(STD_SLOT).valid.getDrivingReg.init(False)
      }
    }
  }
}
