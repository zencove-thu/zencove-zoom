package zencove.core

import zencove.builder._

trait CPU extends MultiPipeline with PipelineAutoBuild {
  type T = CPU
  val pFetch: FetchPipeline = null
  val pDecode: DecodePipeline = null
  val pMemory: MemPipeline = null
  // 在实现中设为null来不生成对应的stage
  // 同时也可以随意在其中插入stage。不过模块的适应度要好才能在插入stage的时候不出错
  val IF1: Stage = null
  val IF2: Stage = null
  val ID: Stage = null
  val RENAME: Stage = null
  val DISPATCH: Stage = null
  val EXE: Stage = null
  val MEM1: Stage = null
  val MEM2: Stage = null
  val WB: Stage = null
  val signals: CPUSignals
}
