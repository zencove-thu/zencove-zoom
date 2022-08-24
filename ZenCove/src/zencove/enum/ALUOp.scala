package zencove.enum

import spinal.core.SpinalEnum

object ALUOp extends SpinalEnum {
  val ADD, ADDU, SUB, SUBU = newElement()
  val AND, OR, XOR, NOR = newElement()
  val SLT, SLTU = newElement()
  val SLL, SRL, SRA = newElement()
  val LU = newElement()
  val CLO, CLZ = newElement()
}
