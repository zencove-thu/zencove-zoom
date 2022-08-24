package zencove.model

import spinal.core._
import spinal.lib._
import zencove.RegFileConfig
import zencove.ZencoveConfig
import zencove.util._

/** ROB表项。
  *
  * ROB主要关心一条指令的执行状态，和需要提交给各个模块的提交信息。
  */
final case class ROBEntry(config: ZencoveConfig, fullState: Boolean = true) extends Bundle {
  val info = ROBEntryInfo(config)
  val state = ROBEntryState(config.regFile, fullState)
}

/** Info信息应当用于提交，不需要被writeback修改。
  *
  * @param config
  */
final case class ROBEntryInfo(config: ZencoveConfig) extends Bundle {
  // ROB不需要完整指令信息
  val uop = ROBMicroOp(config)
  // 似乎只需要rename的写口？用于提交ARF状态
  val rename = ROBRenameRecord(config.regFile)
  // frontend exception的badVA是pc
  val frontendExc = Bool
}

/** State信息关心指令当前执行状态，通常writeback会改变这些状态。
  *
  * @param config
  */
final case class ROBEntryState(config: RegFileConfig, full: Boolean = true) extends Bundle {
  // 完成则代表这条指令已经可以提交
  val complete = Bool
  // 完整异常信息
  val except = Flow(ExceptionPayload(full))
  // 分支预测恢复信息
  val mispredict = Bool
  // LSU检测到uncached区段，需要提交时操作
  val lsuUncached = full generate Bool
  // INT执行结果
  val intResult = full generate UWord()
  // 条件移动的条件判断结果
  val condTrue = Bool
  // TODO: intResult和badVA都是3r1w，可以用reorder ram或lut ram复制法实现
  val actualTaken = Bool
}
