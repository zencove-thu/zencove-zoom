package zencove.core

import zencove.builder._

trait ExecutePipeline extends Pipeline {
  type T = ExecutePipeline
  val ISS: Stage = null // issue/select
  val RRD: Stage = null // register read
  val EXE: Stage = null
  val WB: Stage = null
}
