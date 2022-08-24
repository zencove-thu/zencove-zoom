package zencove.model

import zencove.model._
import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.util._

final case class ROBStateCompletePort(config: ZencoveConfig) extends Bundle {
  val robIdx = UInt(config.rob.robAddressWidth bits)
  val complete = Bool
}

final case class ROBStateLSUPort(config: ZencoveConfig) extends Bundle {
  val robIdx = UInt(config.rob.robAddressWidth bits)
  val except = Flow(ExceptionPayload(true))
  val lsuUncached = Bool
  // val intResult = UWord()
}

final case class ROBStateALUPort(config: ZencoveConfig) extends Bundle {
  val robIdx = UInt(config.rob.robAddressWidth bits)
  val except = Flow(ExceptionPayload(false))
  // val intResult = UWord()
}

final case class ROBStateBRUPort(config: ZencoveConfig) extends Bundle {
  val robIdx = UInt(config.rob.robAddressWidth bits)
  val except = Flow(ExceptionPayload(false))
  val mispredict = Bool
  val intResult = UWord()
  val condTrue = Bool
  val actualTaken = Bool
}
