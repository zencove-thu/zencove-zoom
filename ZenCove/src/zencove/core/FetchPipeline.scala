package zencove.core

import zencove.builder._

trait FetchPipeline extends Pipeline {
  type T = FetchPipeline
  val IF1: Stage = null
  val IF2: Stage = null
  val signals: FetchSignals
}
