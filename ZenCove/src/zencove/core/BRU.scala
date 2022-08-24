package zencove.core

import spinal.core.Component
import spinal.core.Bundle
import zencove.util._
import spinal.core._

class BRU extends Component {
  val io = new Bundle {
    //predicts
    val predictJump = in(Bool)
    val predictAddr = in(UWord())

    //actual
    val isBranch = in(Bool)
    val isJump = in(Bool)
    val isJR = in(Bool)

    val pc = in(UWord())
    val inst = in(Bits(26 bits)) // 去掉高位
    val condition = in(Bool)
    val regSrc = in(UWord())

    val mispredict = out(Bool)
    val actualTarget = out(UWord()) //without prediction, actual target
  }

  import io._
  val delaySlotPC = pc + 4
  val nextPC = pc + 8
  val branchOffset = (inst(15 downto 0).asSInt @@ U(0, 2 bits)).resize(32 bits).asUInt
  val jumpTarget = inst(25 downto 0).asUInt @@ U(0, 2 bits)

  when(isBranch) {
    actualTarget := delaySlotPC + branchOffset
  } elsewhen(isJump) {
    actualTarget := delaySlotPC(31 downto 28) @@ jumpTarget
  } otherwise {
    actualTarget := regSrc
  }

  when(isBranch || isJR || isJump) {
    mispredict := (predictJump ^ (condition || isJR || isJump)) || ((condition || isJR || isJump) && (actualTarget =/= predictAddr))
  } otherwise {
    mispredict := predictJump
  }
}
