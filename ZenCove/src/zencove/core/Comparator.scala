package zencove.core

import zencove.builder.Plugin
import spinal.core._
import zencove.enum._
import zencove.util.UWord
import zencove.ISAConfig
import scala.collection.mutable.ArrayBuffer

class Comparator(isa: ISAConfig) extends Component {
  val io = new Bundle {
    val src1 = in(UWord())
    val src2 = in(UWord())
    val op = in(CompareOp())
    val result = out(Bool)
  }
  import io._
  val eq = src1 === src2
  val ltz = src1.msb
  val eqz = !src1.orR
  val lez = ltz || eqz
  val resultList = ArrayBuffer[(Any, Bool)](
    default -> False,
    CompareOp.EQ -> eq,
    CompareOp.NE -> !eq,
    CompareOp.GEZ -> !ltz,
    CompareOp.GTZ -> !lez,
    CompareOp.LEZ -> lez,
    CompareOp.LTZ -> ltz
  )
  if (isa.useTrap) {
    val ltu = src1 < src2
    val xorSign = src1.msb ^ src2.msb
    val lt = ltu ^ xorSign
    val leu = ltu || eq
    val le = lt || eq
    resultList ++= Seq(
      CompareOp.GE -> !lt,
      CompareOp.GT -> !le,
      CompareOp.LE -> le,
      CompareOp.LT -> lt,
      CompareOp.GEU -> !ltu,
      CompareOp.GTU -> !leu,
      CompareOp.LEU -> leu,
      CompareOp.LTU -> ltu
    )
  }
  result := op.muxList(resultList)
}
