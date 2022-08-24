package zencove.core

import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.util._
import zencove.builder._
import zencove.model._

class FetchBuffer(config: ZencoveConfig) extends Plugin[FetchPipeline] {
  private val frontend = config.frontend
  private val icache = config.frontend.icache
  val bufferFIFO =
    new MultiPortFIFOVec(
      InstBufferEntry(config.frontend),
      frontend.fetchBufferDepth,
      frontend.fetchWidth,
      config.decode.decodeWidth
    ) { //TODO: Imitate from reorder buffer, need recheck.
      val flush = in(Bool)
      when(flush) {
        pushPtr := 0
        popPtr := 0
        isRisingOccupancy := False
        // io.push.foreach(_.setBlocked())
      }
      pushPtr.asOutput()
      popPtr.asOutput()
    }
  def popPorts = bufferFIFO.io.pop
  override def build(pipeline: FetchPipeline): Unit = pipeline.IF2 plug new Area {

    val flush = pipeline.globalService[CommitFlush].needFlush
    bufferFIFO.flush := flush //clear when need flush. Is this OK??

    import pipeline.IF2._
    import pipeline.signals._
    // data有效当且仅当不超过cache line
    val pcWordOffset = input(PC)(icache.wordOffsetRange)
    val isStall = False
    val fetchPacket = input(pipeline.signals.FETCH_PACKET)
    for (i <- 0 until frontend.fetchWidth) {
      val p = bufferFIFO.io.push(i)
      val fetchWord = fetchPacket.insts(i)
      val fetchWordValid = fetchWord.valid && input(pipeline.signals.INSTRUCTION_MASK)(i)
      // 把前端异常挂给第一条指令，后面的指令没有异常，减少fan out
      if (i == 0)
        p.payload.except := fetchPacket.except
      else
        p.payload.except.setIdle()
      p.payload.pc := fetchPacket.pc + i * 4 //Note: i*4 is a constant
      p.payload.inst := fetchWord.payload

      //info needed for modify btb and ras
      p.predInfo.predictBranch := input(pipeline.signals.BRANCH_MASK)(i)
      p.predInfo.predictTaken := input(pipeline.signals.TAKEN_MASK)(i)
      p.predInfo.predictAddr := input(PREDICT_ADDR)
      p.predRecover.recoverTop := input(pipeline.signals.RECOVER_TOP)
      if (!config.frontend.bpu.useHybrid) p.predRecover.predictCounter := input(pipeline.signals.PRED_COUNTER)(i)
      if (config.frontend.bpu.useGlobal) p.predRecover.ghr := input(PRIVATE_BRANCH_HISTORY)(i)
      if (config.frontend.bpu.useHybrid) p.predRecover.hybridRecover := input(HYBRID_RECOVERS)(i)

      // 任何fetch出的word不能进buffer，整个stall住
      isStall setWhen (arbitration.isValid && fetchWordValid && !p.ready)
      p.valid := arbitration.isValidNotStuck && fetchWordValid
    }
    arbitration.haltItself setWhen isStall
  }
}
