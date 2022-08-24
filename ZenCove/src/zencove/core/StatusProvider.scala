package zencove.core

import spinal.core._
import spinal.lib._
import zencove.enum._

trait StatusProvider {
  val privMode: SpinalEnumCraft[PrivMode.type]
  val statusCU0: Bool
  val statusERL: Bool
  val statusEXL: Bool
  val statusBEV: Bool
  def normalLevel = !statusERL && !statusEXL
}

trait IntStatusProvider {
  val intVecOffset: Flow[UInt]
  def intPending = intVecOffset.valid
}
