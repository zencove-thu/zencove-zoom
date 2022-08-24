package zencove.core

import zencove.builder._
import zencove.util.UWord
import zencove.FrontendConfig
import spinal.core._
import spinal.lib._
import scala.collection.mutable.ArrayBuffer

class ProgramCounter(config: FrontendConfig) extends Plugin[FetchPipeline] {
  case class JumpInfo(interface: Stream[UInt], priority: Int)
  private val jumpInfos = ArrayBuffer[JumpInfo]()
  private var predict: Flow[UInt] = null

  /** add a new entry to the jumpInfo
    *
    * @param priority
    *   the larger the higher priority
    * @return
    *   the interface of the jump entry, modify valid and payload to activate branching
    */
  def addJumpInterface(interface: Stream[UInt], priority: Int) {
    jumpInfos += JumpInfo(interface, priority)
  }
  def setPredict(source: Flow[UInt]) {
    predict = source
  }
  val nextPC = UWord()
  val backendJumpInterface = Stream(UWord()).setIdle()
  override def build(pipeline: FetchPipeline): Unit = pipeline.IF1 plug new Area {
    import pipeline.IF1._
    val jumpPipe = backendJumpInterface.s2mPipe()
    jumpPipe.ready := !arbitration.isStuck
    val cacheLineWords = config.icache.lineWords
    val regPC = RegNextWhen(nextPC, arbitration.notStuck, init = UWord(config.pcInit))
    insert(PC) := regPC
    val pcOffset = regPC(2, log2Up(cacheLineWords) bits)
    val fetchWidth = config.fetchWidth
    // 一次fetch不允许跨行，因此最多顶到行尾
    val defaultPC = regPC + (pcOffset.muxList(
      U(fetchWidth),
      ((cacheLineWords - fetchWidth + 1) until cacheLineWords).map { i =>
        (i, U(cacheLineWords - i))
      }
    ) @@ U"2'b00")
    nextPC := defaultPC
    // BTB单周期预测结果
    if (predict != null) {
      when(predict.valid)(nextPC := predict.payload)
    }
    // 后端跳转结果
    when(jumpPipe.valid)(nextPC := jumpPipe.payload)
  }
}
