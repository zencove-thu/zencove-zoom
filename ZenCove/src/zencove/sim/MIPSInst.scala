package zencove.sim

import spinal.core.MaskedLiteral
import zencove.MIPS32

object MIPSInst {
  def setField(inst: StringBuilder, field: Int, offset: Int, length: Int) = {
    assert(inst.size == 32)
    assert(offset + length <= 32)
    var str = field.toBinaryString
    str =
      if (str.size <= length) "0".repeat(length - str.size) + str
      else str.substring(str.size - length)
    assert(str.size == length)
    val begin = 32 - offset - length
    val end = 32 - offset
    for (i <- begin until end)
      if (inst(i) != '-') println(s"Warning: overwrite instruction bit ${i}")
    inst.replace(begin, end, str)
  }
  // | OPCODE(6) | rs(5) | rt(5) | rd(5) | sa(5) | FUNCTION(6) |
  def TypeA(
      inst: MaskedLiteral,
      rs: Int = -1,
      rt: Int = -1,
      rd: Int = -1,
      sa: Int = -1
  ): Long = {
    val builder = new StringBuilder(inst.getBitsString(32, '-'))
    if (rs >= 0) setField(builder, rs, 21, 5)
    if (rt >= 0) setField(builder, rt, 16, 5)
    if (rd >= 0) setField(builder, rd, 11, 5)
    if (sa >= 0) setField(builder, sa, 6, 5)
    java.lang.Long.parseUnsignedLong(builder.result(), 2)
  }
  // | OPCODE(6) | rs(5) | rt(5) | immediate(16) |
  def TypeB(inst: MaskedLiteral, immediate: Int, rs: Int = -1, rt: Int = -1) = {
    val builder = new StringBuilder(inst.getBitsString(32, '-'))
    // assert(immediate.toShort == immediate, "immediate length exceeds 16 bits")
    setField(builder, immediate, 0, 16)
    if (rs >= 0) setField(builder, rs, 21, 5)
    if (rt >= 0) setField(builder, rt, 16, 5)
    java.lang.Long.parseUnsignedLong(builder.result(), 2)
  }
  def TypeCP0(inst: MaskedLiteral, rt: Int, cp0Addr: Int) = {
    val builder = new StringBuilder(inst.getBitsString(32, '-'))
    setField(builder, rt, 16, 5)
    setField(builder, cp0Addr & 0x7, 0, 3)
    setField(builder, cp0Addr >> 3, 11, 5)
    java.lang.Long.parseUnsignedLong(builder.result(), 2)
  }
  // | OPCODE(6) | target(26) |
  def Jump(inst: MaskedLiteral, target: Int) = {
    val builder = new StringBuilder(inst.getBitsString(32, '-'))
    setField(builder, target, 0, 26)
    java.lang.Long.parseUnsignedLong(builder.result(), 2)
  }

  def ADDU(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.ADDU, rs, rt, rd)
  def ADDIU(rt: Int, rs: Int, immediate: Int) =
    TypeB(MIPS32.ADDIU, immediate, rs, rt)
  def BEQ(rs: Int, rt: Int, offset: Int) = TypeB(MIPS32.BEQ, offset, rs, rt)
  def BGEZ(rs: Int, offset: Int) = TypeB(MIPS32.BGEZ, rs = rs, immediate = offset)
  def BGEZAL(rs: Int, offset: Int) = TypeB(MIPS32.BGEZAL, rs = rs, immediate = offset)
  def BGTZ(rs: Int, offset: Int) = TypeB(MIPS32.BGTZ, rs = rs, immediate = offset)
  def BLEZ(rs: Int, offset: Int) = TypeB(MIPS32.BLEZ, rs = rs, immediate = offset)
  def BLTZ(rs: Int, offset: Int) = TypeB(MIPS32.BLTZ, rs = rs, immediate = offset)
  def BLTZAL(rs: Int, offset: Int) = TypeB(MIPS32.BLTZAL, rs = rs, immediate = offset)
  def BNE(rs: Int, rt: Int, offset: Int) = TypeB(MIPS32.BNE, offset, rs, rt)
  def DIV(rs: Int, rt: Int) = TypeA(MIPS32.DIV, rs, rt)
  def DIVU(rs: Int, rt: Int) = TypeA(MIPS32.DIVU, rs, rt)
  def J(target: Int) = Jump(MIPS32.J, target)
  def JAL(target: Int) = Jump(MIPS32.JAL, target)
  def JALR(rs: Int, rd: Int) = TypeA(MIPS32.JALR, rs = rs, rd = rd)
  def JR(rs: Int) = TypeA(MIPS32.JR, rs = rs)
  def LUI(rt: Int, immediate: Int) = TypeB(MIPS32.LUI, immediate, rt = rt)
  def LB(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.LB, offset, base, rt)
  def LBU(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.LBU, offset, base, rt)
  def LH(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.LH, offset, base, rt)
  def LHU(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.LHU, offset, base, rt)
  def LW(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.LW, offset, base, rt)
  def LWL(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.LWL, offset, base, rt)
  def LWR(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.LWR, offset, base, rt)
  def MADD(rs: Int, rt: Int) = TypeA(MIPS32.MADD, rs, rt)
  def MADDU(rs: Int, rt: Int) = TypeA(MIPS32.MADDU, rs, rt)
  def MFC0(rt: Int, cp0Addr: Int) = TypeCP0(MIPS32.MFC0, rt, cp0Addr)
  def MFHI(rd: Int) = TypeA(MIPS32.MFHI, rd = rd)
  def MFLO(rd: Int) = TypeA(MIPS32.MFLO, rd = rd)
  def MSUB(rs: Int, rt: Int) = TypeA(MIPS32.MSUB, rs, rt)
  def MSUBU(rs: Int, rt: Int) = TypeA(MIPS32.MSUBU, rs, rt)
  def MTC0(rt: Int, cp0Addr: Int) = TypeCP0(MIPS32.MTC0, rt, cp0Addr)
  def MTHI(rs: Int) = TypeA(MIPS32.MTHI, rs = rs)
  def MTLO(rs: Int) = TypeA(MIPS32.MTLO, rs = rs)
  def MUL(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.MUL, rs, rt, rd)
  def MULT(rs: Int, rt: Int) = TypeA(MIPS32.MULT, rs, rt)
  def MULTU(rs: Int, rt: Int) = TypeA(MIPS32.MULTU, rs, rt)
  def NOP() = SLL(0, 0, 0)
  def NOR(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.NOR, rs, rt, rd)
  def OR(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.OR, rs, rt, rd)
  def ORI(rt: Int, rs: Int, immediate: Int) =
    TypeB(MIPS32.ORI, immediate, rs, rt)
  def SB(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.SB, offset, base, rt)
  def SH(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.SH, offset, base, rt)
  def SLL(rd: Int, rt: Int, sa: Int) =
    TypeA(MIPS32.SLL, rt = rt, rd = rd, sa = sa)
  def SLLV(rd: Int, rt: Int, rs: Int) = TypeA(MIPS32.SLLV, rs, rt, rd)
  def SLT(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.SLT, rs, rt, rd)
  def SLTI(rt: Int, rs: Int, immediate: Int) =
    TypeB(MIPS32.SLTI, immediate, rs, rt)
  def SLTIU(rt: Int, rs: Int, immediate: Int) =
    TypeB(MIPS32.SLTIU, immediate, rs, rt)
  def SLTU(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.SLTU, rs, rt, rd)
  def SRA(rd: Int, rt: Int, sa: Int) =
    TypeA(MIPS32.SRA, rt = rt, rd = rd, sa = sa)
  def SRAV(rd: Int, rt: Int, rs: Int) = TypeA(MIPS32.SRAV, rs, rt, rd)
  def SRL(rd: Int, rt: Int, sa: Int) =
    TypeA(MIPS32.SRL, rt = rt, rd = rd, sa = sa)
  def SRLV(rd: Int, rt: Int, rs: Int) = TypeA(MIPS32.SRLV, rs, rt, rd)
  def SSNOP() = SLL(0, 0, 1)
  def SUB(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.SUB, rs, rt, rd)
  def SUBU(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.SUBU, rs, rt, rd)
  def SW(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.SW, offset, base, rt)
  def SWL(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.SWL, offset, base, rt)
  def SWR(rt: Int, base: Int, offset: Int) = TypeB(MIPS32.SWR, offset, base, rt)
  def TLBP() = TypeA(MIPS32.TLBP)
  def TLBR() = TypeA(MIPS32.TLBR)
  def TLBWI() = TypeA(MIPS32.TLBWI)
  def TLBWR() = TypeA(MIPS32.TLBWR)
  def XOR(rd: Int, rs: Int, rt: Int) = TypeA(MIPS32.XOR, rs, rt, rd)
  def XORI(rt: Int, rs: Int, immediate: Int) =
    TypeB(MIPS32.XORI, immediate, rs, rt)
}
