package zencove.core

import zencove.ZencoveConfig
import zencove.builder.Stageable
import spinal.core._
import spinal.lib._
import zencove.enum._
import zencove.model._

class DecodeSignals(config: ZencoveConfig) {
  private val decodeWidth = config.decode.decodeWidth
  object DECODE_PACKET extends Stageable(Vec(Flow(MicroOp(config)), decodeWidth))
  object RENAME_RECORDS extends Stageable(Vec(RenameRecord(config.regFile), decodeWidth))
  object ROB_INDEXES extends Stageable(Vec(UInt(config.rob.robAddressWidth bits), decodeWidth))
  object HILO_RENAME_RECORDS extends Stageable(Vec(UInt(config.hlu.prfAddrWidth bits), decodeWidth))
}
