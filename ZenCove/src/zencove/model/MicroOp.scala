package zencove.model

import spinal.core._
import spinal.lib._
import zencove.builder.Stageable
import zencove.enum._
import zencove.util._
import zencove.ZencoveConfig

object MicroOpSignals {
  // 用同样的名字构造stageable，这样便于decoder编写
  // 与micro op保持一样的顺序，便于对照
  object fuType extends Stageable(FUType())
  object useRs extends Stageable(Bool)
  object useRt extends Stageable(Bool)
  object wbSel extends Stageable(RegWriteAddr())
  object immExtendType extends Stageable(ExtendType())
  object genOverflow extends Stageable(Bool)
  object aluOp extends Stageable(ALUOp())
  object lsType extends Stageable(LoadStoreType())
  object isLoad extends Stageable(Bool)
  object isStore extends Stageable(Bool)
  object cmpOp extends Stageable(CompareOp())
  object isBranch extends Stageable(Bool)
  object isJump extends Stageable(Bool)
  object isJR extends Stageable(Bool)
  object isTrap extends Stageable(Bool)
  object tlbOp extends Stageable(TLBOp())
  object operateCache extends Stageable(Bool)
  object cacheOp extends Stageable(CacheOp())
  object cacheSel extends Stageable(CacheSel())
  object writeCP0 extends Stageable(Bool)
  object readCP0 extends Stageable(Bool)
  object isWait extends Stageable(Bool)
  object readHiLo extends Stageable(Bool)
  object opHiLo extends Stageable(HiLoWriteType())
  object signed extends Stageable(Signed())
  object isEret extends Stageable(Bool)
  object isCondMove extends Stageable(Bool)
  // 也可以添加不在micro op中的信号，则会产生insert，可以自行处理
  object isSyscall extends Stageable(Bool)
  object isBreak extends Stageable(Bool)
}

/** decode生成的一个micro op的信息。
  */
final case class MicroOp(config: ZencoveConfig) extends Bundle {
  private val arfAddrWidth = config.regFile.arfAddrWidth
  private val rasEntries = config.frontend.btb.rasEntries
  val pc = UWord()
  val inst = BWord()
  // 前端分支预测信息
  // 提供预测是否是branch，用于恢复指令修改后在非branch的指令上预测branch
  val predInfo = BranchPredictInfo()
  val predRecover = PredictRecoverBundle(config.frontend)
  // dispatch信息
  val fuType = FUType()
  // REG r/w信息
  val useRs = Bool
  val useRt = Bool
  // val wbSel = RegWriteAddr()
  val wbAddr = UInt(arfAddrWidth bits)
  val doRegWrite = Bool
  // IMM信息
  val immExtendType = ExtendType()
  // ALU信息
  val genOverflow = Bool
  val aluOp = ALUOp()
  // LSU信息
  val lsType = LoadStoreType()
  val isLoad = Bool
  val isStore = Bool
  // BRU信息
  val cmpOp = CompareOp()
  val isBranch = Bool
  val isJump = Bool
  val isJR = Bool
  val isTrap = Bool
  val branchLike = Bool
  // TLB操作
  val tlbOp = TLBOp()
  def operateTLB = tlbOp =/= TLBOp.NONE
  // cache操作
  val operateCache = Bool
  // CP0操作
  val writeCP0 = Bool
  val readCP0 = Bool
  // WAIT指令
  val isWait = Bool
  // commit信息
  val uniqueRetire = Bool
  // HI/LO信息
  val readHiLo = Bool
  val opHiLo = HiLoWriteType()
  def writeHiLo = opHiLo =/= HiLoWriteType.NONE
  // mul/div信息
  val signed = Signed()
  // 异常信息，事实上此处仍然不需要bad va
  val except = Flow(ExceptionPayload(false))
  val isEret = Bool
  // 条件移动指令
  val isCondMove = Bool
  // 非branch的控制流转移
  val flushState = Bool

  def needNotExecute = except.valid || fuType === FUType.NONE
}

/** IQ所需的micro op。必须是MicroOp的子集。
  */
final case class IntIQMicroOp() extends Bundle {
  // val pc = UWord()
  // 前端分支预测信息
  // 提供预测是否是branch，用于恢复指令修改后在非branch的指令上预测branch
  val predInfo = BranchPredictInfo()
  // dispatch信息
  val fuType = FUType()
  // 转成PRF了，ARF地址不再需要
  val useRs = Bool
  val useRt = Bool
  val doRegWrite = Bool
  // IMM信息
  val immExtendType = ExtendType()
  // ALU信息
  val genOverflow = Bool
  val aluOp = ALUOp()
  // BRU信息
  val cmpOp = CompareOp()
  val isBranch = Bool
  val isJump = Bool
  val isJR = Bool
  def branchLike = isBranch || isJump || isJR
  val isTrap = Bool
  // CP0操作
  val writeCP0 = Bool
  val readCP0 = Bool
  // 条件移动指令
  val isCondMove = Bool
  // WAIT指令不过FU，可删
  // 旁路信息并不重要，FU自有定夺
  // commit信息与FU无关
  // 异常信息另行保存
  // ERET也不过FU

  // 需要单独赋值
  val partialInst = Bits(26 bits)
  val intIQpc = UInt(30 bits)
}

/** 乘除法IQ所需的micro op。必须是MicroOp的子集。
  */
final case class MulDivIQMicroOp() extends Bundle {
  // dispatch信息
  val fuType = FUType()
  // REG r/w信息
  val doRegWrite = Bool
  // HI/LO信息
  val readHiLo = Bool
  val opHiLo = HiLoWriteType()
  def writeHiLo = opHiLo =/= HiLoWriteType.NONE
  // mul/div信息
  val signed = Signed()
}

/** Mem IQ所需的micro op。必须是MicroOp的子集。
  */
final case class MemIQMicroOp() extends Bundle {
  // 转成PRF了，ARF地址不再需要
  val doRegWrite = Bool
  // LSU信息
  val lsType = LoadStoreType()
  val isLoad = Bool
  val isStore = Bool
  // cache操作
  // val operateCache = Bool

  // 需要单独赋值
  val immField = Bits(16 bits)
  val cacheOp = CacheOp()
  val cacheSel = CacheSel()
}

/** ROB所需的micro op。必须是MicroOp的子集。
  */
final case class ROBMicroOp(config: ZencoveConfig) extends Bundle {
  private val arfAddrWidth = config.regFile.arfAddrWidth
  private val rasEntries = config.frontend.btb.rasEntries
  val pc = UWord()
  // 前端分支预测信息
  // 提供预测是否是branch，用于恢复指令修改后在非branch的指令上预测branch
  val predInfo = BranchPredictInfo(false)
  val predRecover = PredictRecoverBundle(config.frontend)
  // 写寄存器
  val wbAddr = UInt(arfAddrWidth bits)
  val doRegWrite = Bool
  // IMM、ALU、LSU、BRU信息均与执行相关，仅部分保留
  // LSU信息
  val isLoad = Bool
  val isStore = Bool
  // BRU信息
  val isBranch = Bool
  val isJump = Bool
  val isJR = Bool
  val branchLike = Bool
  // TLB操作
  val tlbOp = TLBOp()
  def operateTLB = tlbOp =/= TLBOp.NONE
  // cache操作
  val operateCache = Bool
  // CP0操作
  val writeCP0 = Bool
  // WAIT指令
  val isWait = Bool
  // commit信息
  val uniqueRetire = Bool
  // 异常被另行保存
  val isEret = Bool
  // 条件移动指令
  val isCondMove = Bool
  // 非branch的控制流转移
  val flushState = Bool

  // 需要单独赋值的域
  val writeHiLo = Bool
  // 5-bit cache op / 8-bit cp0 address
  val neededInstField = Bits(8 bits)
}
