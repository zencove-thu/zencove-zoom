package zencove.model

import spinal.core._
import zencove.util._

final case class ExceptionPayload(genBadVA: Boolean) extends Bundle {
  val code = Bits(5 bits)
  val badVA = genBadVA generate UWord()
  val isTLBRefill = Bool
}
