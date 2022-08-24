package zencove.model

import spinal.core._
import zencove.util._

final case class BranchPredictInfo(withAddr: Boolean = true) extends Bundle {
  val predictBranch = Bool
  val predictTaken = Bool
  // val predictAddr = UWord()
  val predictAddr = withAddr generate UWord()
}
