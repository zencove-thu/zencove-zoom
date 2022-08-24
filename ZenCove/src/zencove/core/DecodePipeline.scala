package zencove.core

import zencove.builder._

trait DecodePipeline extends Pipeline {
  type T = DecodePipeline
  val ID: Stage = null
  val RENAME: Stage = null
  val DISPATCH: Stage = null
  val signals: DecodeSignals
}
