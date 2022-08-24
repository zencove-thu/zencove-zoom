package zencove.core

import zencove.builder._
import zencove.model._
import zencove.enum._
import zencove.util.UWord
import spinal.core._
import zencove.MIPS32.ExceptionCode
import zencove.util.BWord
import spinal.lib.PriorityMux
import zencove.ISAConfig

class ALU(isa: ISAConfig) extends Component {
  val io = new Bundle {
    val src1 = in(UWord())
    val src2 = in(UWord())
    val sa = in(UInt(5 bits))
    val op = in(ALUOp())
    val result = out(UWord())
    val overflow = out(False)
  }
  import io._

  def clzImpl(src: BitVector) = {
    val bitCount = log2Up(src.getBitsWidth + 1).bits
    PriorityMux(
      src.asBools.reverse.zipWithIndex.map { case (b, i) =>
        b -> U(i, bitCount)
      } :+ (True -> U(src.getBitsWidth, bitCount))
    ).setCompositeName(src, "clz", true)
  }

  switch(op) {
    import ALUOp._
    is(ADD) {
      result := src1 + src2
      // 同号加，结果号不同
      overflow.setWhen(src1.msb === src2.msb && src1.msb =/= result.msb)
    }
    is(ADDU) {
      result := src1 + src2
    }
    is(SUB) {
      result := src1 - src2
      // src1 + -src2
      overflow.setWhen(src1.msb =/= src2.msb && src1.msb =/= result.msb)
    }
    is(SUBU) {
      result := src1 - src2
    }
    is(AND) {
      result := src1 & src2
    }
    is(OR) {
      result := src1 | src2
    }
    is(XOR) {
      result := src1 ^ src2
    }
    is(NOR) {
      result := ~(src1 | src2)
    }
    is(SLT) {
      result := (src1.asSInt < src2.asSInt).asUInt.resized
    }
    is(SLTU) {
      result := (src1 < src2).asUInt.resized
    }
    is(SLL) {
      result := src2 |<< sa
    }
    is(SRL) {
      result := src2 |>> sa
    }
    is(SRA) {
      result := (src2.asSInt >> sa).asUInt
    }
    is(LU) {
      result := src2(15 downto 0) @@ U(0, 16 bits)
    }

    if (isa.useCLO) {
      val clzSrc = UWord().assignDontCare()
      val clzResult = clzImpl(clzSrc).resize(32)
      is(CLO) {
        clzSrc := ~src1
        result := clzResult
      }
      is(CLZ) {
        clzSrc := src1
        result := clzResult
      }
    } else {
      default {
        result.assignDontCare()
      }
    }

  }
}
