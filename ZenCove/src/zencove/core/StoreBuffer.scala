package zencove.core

import zencove.builder._
import zencove.ZencoveConfig
import spinal.core._
import spinal.lib._
import zencove.model.StoreBufferSlot
import zencove.util._

/** Store buffer. 压缩式FIFO。
  *
  * @param config
  */
class StoreBuffer(config: ZencoveConfig) extends Plugin[MemPipeline] {
  val depth = config.storeBufferDepth
  val slotType = HardType(StoreBufferSlot(config))
  val queue = Vec(RegFlow(slotType()), depth)
  val queueNext = CombInit(queue)
  val queueIO = new Area {
    val pushPort = Stream(slotType)
    val popPort = Stream(slotType).setBlocked()
    popPort.valid := queue(0).valid
    popPort.payload := queue(0).payload
  }

  // 查询逻辑
  val query = new Area {
    val addr = UWord()
    val data = BWord().assignDontCare()
    val be = B(0, 4 bits)
    // 新覆盖老
    for (i <- 0 until depth)
      when(
        addr(2, 30 bits) === queue(i).addr(2, 30 bits) &&
          queue(i).valid && queue(i).isStore && queue(i).isCached
      ) {
        for (j <- 0 until 4)
          when(queue(i).be(j)) {
            be(j) := True
            data(j * 8, 8 bits) := queue(i).data(j * 8, 8 bits)
          }
      }
  }

  override def build(pipeline: MemPipeline): Unit = pipeline plug new Area {
    // retire
    val commitStore = pipeline.globalService[StoreBufferCommit].commitStore
    val retireFall = !queue(0).retired +: (for (i <- 1 until depth)
      yield queue(i - 1).retired && !queue(i).retired)
    for (j <- 0 until depth) {
      when(commitStore && retireFall(j)) {
        // 定位匹配，则将槽retire
        if (config.storeBufferAlterImpl) queue(j).retired := True
        queueNext(j).retired := True
      }
    }

    // 入队逻辑
    val validFall = !queue(0).valid +: (for (i <- 1 until depth)
      yield queue(i - 1).valid && !queue(i).valid)
    queueIO.pushPort.ready := !queue.last.valid
    for (j <- 0 until depth) {
      when(queueIO.pushPort.valid && validFall(j)) {
        // 定位匹配，则将槽入队
        if (config.storeBufferAlterImpl) queue(j).push(queueIO.pushPort.payload)
        queueNext(j).push(queueIO.pushPort.payload)
      }
    }

    val flush = pipeline.globalService[CommitFlush].regFlush
    when(flush) {
      for (i <- 0 until depth) when(!queueNext(i).payload.retired) {
        if (config.storeBufferAlterImpl) queue(i).valid := False
        queueNext(i).valid := False
      }
    }

    // 压缩逻辑
    for (i <- 0 until depth) {
      if (!config.storeBufferAlterImpl) queue(i) := queueNext(i)
      when(queueIO.popPort.fire) {
        if (i + 1 < depth) queue(i) := queueNext(i + 1)
        else queue(i).valid := False
      }
    }
  }
}
