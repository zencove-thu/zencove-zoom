package zencove.core

import spinal.core._
import spinal.lib._
import zencove.builder._
import zencove.MIPS32
import zencove.model._
import zencove.enum._
import zencove.ISAConfig

class MIPS32Decode(config: ISAConfig) extends Plugin[DecodePipeline] {
  override def setup(pipeline: DecodePipeline): Unit = {
    import MicroOpSignals._
    import MIPS32._
    val decoder = pipeline.service[DecoderArray]
    import decoder._
    // 保持与micro op一样的顺序
    defaultDontCare(aluOp, lsType, cmpOp, signed)
    defaultFalse(useRs, useRt, isLoad, isStore, isBranch, isJump, isJR, isTrap)
    defaultFalse(operateCache, writeCP0, readCP0, isWait, isEret, isCondMove)
    defaultFalse(readHiLo, genOverflow)
    defaultFalse(isSyscall, isBreak)
    setDefault(fuType, FUType.ALU)
    setDefault(wbSel, RegWriteAddr.NONE)
    setDefault(immExtendType, ExtendType.SIGN)
    setDefault(tlbOp, TLBOp.NONE)
    setDefault(opHiLo, HiLoWriteType.NONE)
    val reg2RActions = Seq[(Stageable[_ <: Data], Any)](useRs -> True, useRt -> True)
    val reg2Actions = reg2RActions ++ Seq(wbSel -> RegWriteAddr.RD)
    val regImmRActions = Seq[(Stageable[_ <: Data], Any)](useRs -> True)
    val regImmActions = regImmRActions ++ Seq(wbSel -> RegWriteAddr.RT)
    val regImmZeroActions =
      Seq(useRs -> True, wbSel -> RegWriteAddr.RT, immExtendType -> ExtendType.ZERO)
    // GPR[rd] <- GPR[rs] op GPR[rt]
    addAll(
      ADD -> (reg2Actions ++ Seq(aluOp -> ALUOp.ADD, genOverflow -> True)),
      ADDU -> (reg2Actions ++ Seq(aluOp -> ALUOp.ADDU)),
      SUB -> (reg2Actions ++ Seq(aluOp -> ALUOp.SUB, genOverflow -> True)),
      SUBU -> (reg2Actions ++ Seq(aluOp -> ALUOp.SUBU)),
      SLT -> (reg2Actions ++ Seq(aluOp -> ALUOp.SLT)),
      SLTU -> (reg2Actions ++ Seq(aluOp -> ALUOp.SLTU)),
      AND -> (reg2Actions ++ Seq(aluOp -> ALUOp.AND)),
      OR -> (reg2Actions ++ Seq(aluOp -> ALUOp.OR)),
      XOR -> (reg2Actions ++ Seq(aluOp -> ALUOp.XOR)),
      NOR -> (reg2Actions ++ Seq(aluOp -> ALUOp.NOR)),
      // GPR[rd] <- GPR[rt] op GPR[rs]
      // note: shift操作数顺序是反过来的，但不影响decode
      SLLV -> (reg2Actions ++ Seq(aluOp -> ALUOp.SLL)),
      SRLV -> (reg2Actions ++ Seq(aluOp -> ALUOp.SRL)),
      SRAV -> (reg2Actions ++ Seq(aluOp -> ALUOp.SRA))
    )
    addAll(
      // GPR[rt] <- GPR[rs] op sign_extend(immediate)
      ADDI -> (regImmActions ++ Seq(aluOp -> ALUOp.ADD, genOverflow -> True)),
      ADDIU -> (regImmActions ++ Seq(aluOp -> ALUOp.ADDU)),
      SLTI -> (regImmActions ++ Seq(aluOp -> ALUOp.SLT)),
      SLTIU -> (regImmActions ++ Seq(aluOp -> ALUOp.SLTU)),
      // GPR[rt] <- GPR[rs] op zero_extend(immediate)
      ANDI -> (regImmZeroActions ++ Seq(aluOp -> ALUOp.AND)),
      ORI -> (regImmZeroActions ++ Seq(aluOp -> ALUOp.OR)),
      XORI -> (regImmZeroActions ++ Seq(aluOp -> ALUOp.XOR))
    )
    // LUI: GPR[rt] <- immediate << 16
    add(LUI, Seq(useRt -> False, wbSel -> RegWriteAddr.RT, aluOp -> ALUOp.LU))
    val shiftActions = Seq(useRt -> True, wbSel -> RegWriteAddr.RD)
    // GPR[rd] <- GPR[rt] op sa
    addAll(
      SLL -> (shiftActions ++ Seq(aluOp -> ALUOp.SLL)),
      SRL -> (shiftActions ++ Seq(aluOp -> ALUOp.SRL)),
      SRA -> (shiftActions ++ Seq(aluOp -> ALUOp.SRA))
    )
    // GPR[rt/rd] <- op(GPR[rs])
    if (config.useCLO)
      addAll(
        CLO -> (regImmActions ++ Seq(aluOp -> ALUOp.CLO)),
        CLZ -> (regImmActions ++ Seq(aluOp -> ALUOp.CLZ))
      )
    // trap GPR[rs] op GPR[rt] / trap GPR[rs] op sign_extend(immediate)
    val trapActions = Seq[(Stageable[_ <: Data], Any)](fuType -> FUType.CMP, isTrap -> True)
    val trapReg2Actions = trapActions ++ reg2RActions
    val trapRegImmActions = trapActions ++ regImmRActions
    if (config.useTrap)
      addAll(
        TEQ -> (trapReg2Actions ++ Seq(cmpOp -> CompareOp.EQ)),
        TEQI -> (trapRegImmActions ++ Seq(cmpOp -> CompareOp.EQ)),
        TGE -> (trapReg2Actions ++ Seq(cmpOp -> CompareOp.GE)),
        TGEI -> (trapRegImmActions ++ Seq(cmpOp -> CompareOp.GE)),
        TGEIU -> (trapRegImmActions ++ Seq(cmpOp -> CompareOp.GEU)),
        TGEU -> (trapReg2Actions ++ Seq(cmpOp -> CompareOp.GEU)),
        TLT -> (trapReg2Actions ++ Seq(cmpOp -> CompareOp.LT)),
        TLTI -> (trapRegImmActions ++ Seq(cmpOp -> CompareOp.LT)),
        TLTIU -> (trapRegImmActions ++ Seq(cmpOp -> CompareOp.LTU)),
        TLTU -> (trapReg2Actions ++ Seq(cmpOp -> CompareOp.LTU)),
        TNE -> (trapReg2Actions ++ Seq(cmpOp -> CompareOp.NE)),
        TNEI -> (trapRegImmActions ++ Seq(cmpOp -> CompareOp.NE))
      )
    // SYSCALL
    add(SYSCALL, Seq(isSyscall -> True))
    // BREAK
    add(BREAK, Seq(isBreak -> True))
    // ERET
    val noExeActions = Seq[(Stageable[_ <: Data], Any)](fuType -> FUType.NONE)
    add(ERET, noExeActions :+ (isEret -> True))
    // WAIT
    if (config.useWAIT) add(WAIT, noExeActions :+ (isWait -> True))
    // branch GPR[rs] op GPR[rt] / branch GPR[rs] op zero, optional RA <- PC+8
    val brActions = Seq(fuType -> FUType.CMP, isBranch -> True)
    val brReg2Actions = brActions ++ reg2RActions
    val brRegImmActions = brActions ++ regImmRActions
    val linkActions = Seq(wbSel -> RegWriteAddr.R31)
    val balActions = brRegImmActions ++ linkActions
    addAll(
      BEQ -> (brReg2Actions ++ Seq(cmpOp -> CompareOp.EQ)),
      BGEZ -> (brRegImmActions ++ Seq(cmpOp -> CompareOp.GEZ)),
      BGEZAL -> (balActions ++ Seq(cmpOp -> CompareOp.GEZ)),
      BGTZ -> (brRegImmActions ++ Seq(cmpOp -> CompareOp.GTZ)),
      BLEZ -> (brRegImmActions ++ Seq(cmpOp -> CompareOp.LEZ)),
      BLTZ -> (brRegImmActions ++ Seq(cmpOp -> CompareOp.LTZ)),
      BLTZAL -> (balActions ++ Seq(cmpOp -> CompareOp.LTZ)),
      BNE -> (brReg2Actions ++ Seq(cmpOp -> CompareOp.NE))
    )
    // jump
    val jActions = Seq(fuType -> FUType.CMP, isJump -> True)
    val jrActions = Seq(fuType -> FUType.CMP, isJR -> True, useRs -> True)
    addAll(
      // jump, optional RA <- PC+8
      J -> jActions,
      JAL -> (jActions ++ linkActions),
      // jump GPR[rs], optional GPR[rd] <- PC+8
      JALR -> (jrActions ++ Seq(wbSel -> RegWriteAddr.RD)),
      JR -> jrActions
    )
    // load: GPR[rt] <- MEM[GPR[rs/base] + offset]
    // store: MEM[GPR[rs/base] + offset] <- GPR[rt]
    val memActions = Seq[(Stageable[_ <: Data], Any)](fuType -> FUType.LSU)
    val loadActions = memActions ++ regImmActions ++ Seq(isLoad -> True)
    val storeActions = memActions ++ reg2RActions ++ Seq(isStore -> True)
    val loadUnalignedActions = loadActions ++ Seq(useRt -> True)
    addAll(
      LB -> (loadActions ++ Seq(lsType -> LoadStoreType.BYTE)),
      LBU -> (loadActions ++ Seq(lsType -> LoadStoreType.BYTE_U)),
      LH -> (loadActions ++ Seq(lsType -> LoadStoreType.HALF)),
      LHU -> (loadActions ++ Seq(lsType -> LoadStoreType.HALF_U)),
      LW -> (loadActions ++ Seq(lsType -> LoadStoreType.WORD))
    )
    addAll(
      LWL -> (loadUnalignedActions ++ Seq(lsType -> LoadStoreType.LEFT)),
      LWR -> (loadUnalignedActions ++ Seq(lsType -> LoadStoreType.RIGHT))
    )
    addAll(
      SB -> (storeActions ++ Seq(lsType -> LoadStoreType.BYTE)),
      SH -> (storeActions ++ Seq(lsType -> LoadStoreType.HALF)),
      SW -> (storeActions ++ Seq(lsType -> LoadStoreType.WORD)),
      SWL -> (storeActions ++ Seq(lsType -> LoadStoreType.LEFT)),
      SWR -> (storeActions ++ Seq(lsType -> LoadStoreType.RIGHT))
    )
    val cp0Actions = Seq[(Stageable[_ <: Data], Any)](fuType -> FUType.CP0)
    addAll(
      TLBP -> (noExeActions :+ (tlbOp -> TLBOp.PROBE)),
      TLBR -> (noExeActions :+ (tlbOp -> TLBOp.READ)),
      TLBWI -> (noExeActions :+ (tlbOp -> TLBOp.WRITE_INDEX)),
      TLBWR -> (noExeActions :+ (tlbOp -> TLBOp.WRITE_RANDOM))
    )
    val cacheActions = memActions ++ regImmRActions :+ (operateCache -> True)
    // CACHE
    add(CACHE, cacheActions)
    // SYNC, PREF as nop
    if (config.useSYNC) add(SYNC, noExeActions)
    if (config.usePREF) add(PREF, noExeActions)
    // MFC0: GPR[rt] <- CPR[0,rd,sel]
    add(MFC0, cp0Actions ++ Seq(readCP0 -> True, wbSel -> RegWriteAddr.RT))
    // MTC0: CPR[0,rd,sel] <- GPR[rt]
    add(MTC0, cp0Actions ++ Seq(useRt -> True, writeCP0 -> True))
    // GPR[rd] <- HI/LO, S=LO, U=HI
    val mfhlActions = Seq(fuType -> FUType.HLU, readHiLo -> True, wbSel -> RegWriteAddr.RD)
    addAll(
      MFLO -> (mfhlActions :+ (signed -> Signed.S)),
      MFHI -> (mfhlActions :+ (signed -> Signed.U))
    )
    // HI/LO <- GPR[rs]
    // MTHI should preserve Lo, so we read previous HiLo result
    val mthlActions =
      regImmRActions ++ Seq(fuType -> FUType.HLU, readHiLo -> True, opHiLo -> HiLoWriteType.WRITE)
    addAll(
      MTLO -> (mthlActions :+ (signed -> Signed.S)),
      MTHI -> (mthlActions :+ (signed -> Signed.U))
    )
    val mulBaseActions = reg2RActions :+ (fuType -> FUType.MUL)
    val mulSActions = mulBaseActions :+ (signed -> Signed.S)
    val mulUActions = mulBaseActions :+ (signed -> Signed.U)
    // GPR[rd] <- GPR[rs] * GPR[rt]
    if (config.useMUL) add(MUL, mulSActions ++ Seq(wbSel -> RegWriteAddr.RD))
    // (HI, LO) <- GPR[rs] op GPR[rt]
    add(MULT, mulSActions ++ Seq(opHiLo -> HiLoWriteType.WRITE))
    add(MULTU, mulUActions ++ Seq(opHiLo -> HiLoWriteType.WRITE))
    // (HI, LO) <- (HI, LO) op (GPR[rs] op GPR[rt])
    if (config.useMADD) {
      add(MADD, mulSActions ++ Seq(readHiLo -> True, opHiLo -> HiLoWriteType.ADD))
      add(MADDU, mulUActions ++ Seq(readHiLo -> True, opHiLo -> HiLoWriteType.ADD))
      add(MSUB, mulSActions ++ Seq(readHiLo -> True, opHiLo -> HiLoWriteType.SUB))
      add(MSUBU, mulUActions ++ Seq(readHiLo -> True, opHiLo -> HiLoWriteType.SUB))
    }
    // (r, q) <- GPR[rs] op GPR[rt], HI <- r, LO <- q
    val divBaseActions = reg2RActions ++ Seq(fuType -> FUType.DIV, opHiLo -> HiLoWriteType.WRITE)
    add(DIV, divBaseActions :+ (signed -> Signed.S))
    add(DIVU, divBaseActions :+ (signed -> Signed.U))
    if (config.useCondMove) {
      val condMoveActions = reg2Actions ++ Seq(isCondMove -> True, fuType -> FUType.CMP)
      addAll(
        MOVN -> (condMoveActions :+ (cmpOp -> CompareOp.NEZ)),
        MOVZ -> (condMoveActions :+ (cmpOp -> CompareOp.EQZ))
      )
    }
  }
  override def build(pipeline: DecodePipeline): Unit = {}
}
