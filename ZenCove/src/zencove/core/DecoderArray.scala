package zencove.core

import zencove.builder._
import spinal.core._
import scala.collection.mutable
import zencove.MIPS32.ExceptionCode
import zencove.model._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.enum._
import zencove.MIPS32

/** Decoder service.
  *
  * @param config
  * @param popPorts
  *   Pop ports of fetch buffer.
  */
class DecoderArray(config: ZencoveConfig, val popPorts: Vec[Stream[InstBufferEntry]])
    extends Plugin[DecodePipeline] {
  private val width = config.decode.decodeWidth
  private val defaults =
    mutable.LinkedHashMap[Stageable[_ <: Data], Data]()
  private val encodings =
    mutable.LinkedHashMap[MaskedLiteral, mutable.ArrayBuffer[(Stageable[_ <: Data], Data)]]()
  private val priorities = mutable.LinkedHashMap[MaskedLiteral, Int]()
  private val stageables = mutable.HashSet[Stageable[_ <: Data]]()

  /** 添加多个指令编码。
    *
    * @param encoding
    *   指令编码序列
    */
  def addAll(
      encodings: (MaskedLiteral, Seq[(Stageable[_ <: Data], Any)])*
  ): Unit = encodings.foreach(e => this.add(e._1, e._2))

  /** 添加一条指令编码。
    *
    * @param key
    *   指令编码
    * @param values
    *   解码信号值
    */
  def add(
      key: MaskedLiteral,
      values: Seq[(Stageable[_ <: Data], Any)]
  ): Unit = {
    if (encodings.contains(key))
      println(s"Warning: already added encoding ${key.getBitsString(32, '-')}")
    val instructionModel = encodings.getOrElseUpdate(key, mutable.ArrayBuffer())
    values.map { case (a, b) =>
      assert(!instructionModel.contains(a), s"Over specification of $a")
      b match {
        case value: Data                 => instructionModel += (a -> value)
        case value: SpinalEnumElement[_] => instructionModel += (a -> value())
        case _                           => throw new AssertionError(b.getClass().getName())
      }
    }
  }

  /** 设置默认解码。
    *
    * @param key
    *   信号key
    * @param value
    *   信号默认值
    */
  def setDefault[T <: Data](key: Stageable[T], value: T): Unit = {
    assert(!defaults.contains(key))
    defaults(key) = value
  }
  def setDefault[T <: SpinalEnum](
      key: Stageable[SpinalEnumCraft[T]],
      value: SpinalEnumElement[T]
  ): Unit = {
    assert(!defaults.contains(key))
    defaults(key) = value()
  }

  private def setDefaultDontCare[T <: Data](key: Stageable[T]): Unit =
    setDefault(key, key().assignDontCare())

  /** 设置默认解码为don't care。
    *
    * @param key
    *   信号key
    */
  def defaultDontCare(keys: Stageable[_ <: Data]*) {
    keys.foreach(setDefaultDontCare(_))
  }

  /** 设置默认值为false。
    *
    * @param keys
    */
  def defaultFalse(keys: Stageable[Bool]*) {
    keys.foreach(setDefault(_, False))
  }

  /** 设置解码优先级。用于一个编码下的子编码（例如SSNOP）
    *
    * @param key
    *   指令编码
    * @param priority
    *   优先级，越大优先级越高
    */
  def setPriority(key: MaskedLiteral, priority: Int) {
    priorities(key) = priority
  }

  override def build(pipeline: DecodePipeline): Unit = pipeline.ID plug new Area {
    import pipeline.ID._
    val excHandler = pipeline.globalService[ExceptionHandler]
    // build stageable list
    stageables ++= encodings.flatMap(_._2.map(_._1))
    val noDefaultList = mutable.ArrayBuffer[Stageable[_ <: Data]]()
    for (s <- stageables) {
      if (!defaults.contains(s)) {
        noDefaultList += s
      }
    }
    if (!noDefaultList.isEmpty) {
      val msg = noDefaultList.mkString(", ") + " have no default decode value"
      throw new Exception(msg)
    }
    for (s <- defaults.keys) {
      stageables += s
    }

    val normalEncodings =
      mutable.ArrayBuffer[(MaskedLiteral, mutable.ArrayBuffer[(Stageable[_ <: Data], Data)])]()
    val priorEncodings = mutable.ArrayBuffer[(Int, MaskedLiteral)]()
    for ((encoding, actions) <- encodings) {
      priorities.get(encoding) match {
        case Some(value) => priorEncodings += (value -> encoding)
        case None        => normalEncodings += (encoding -> actions)
      }
    }
    val encodingsByPriority = priorEncodings.sortBy(_._1).map(_._2)

    def buildSingleDecoder(input: InstBufferEntry) =
      new Composite(input) {
        val entry = input
        val illegalEncoding = True
        val uop = MicroOp(config)
        private val uopElements = uop.elements.toMap
        val instruction = entry.inst
        val fields = InstructionParser(instruction)
        val locals = mutable.LinkedHashMap[Stageable[Data], Data]()
        def local[T <: Data](key: Stageable[T]): T = locals
          .getOrElseUpdate(key.asInstanceOf[Stageable[Data]], key())
          .asInstanceOf[T]
        // set defaults
        for ((k, v) <- defaults) uopElements.get(k.getName()) match {
          case Some(value) => value.assignFrom(v)
          case None        => local(k).assignFrom(v)
        }
        // decode
        def decodeInstruction(
            encoding: MaskedLiteral,
            actions: Seq[(Stageable[_ <: Data], Data)]
        ) {
          when(instruction === encoding) {
            // 如果要增加Verilog可读性，则通过类似Stageable的方式获取编码的指令名，给when条件setName
            // 全覆盖的错误可以通过assignment overlap检查出（通常出现于复制忘了改）
            illegalEncoding.clear()
            for ((k, v) <- actions) uopElements.get(k.getName()) match {
              case Some(value) => value.assignFrom(v)
              case None        => local(k).assignFrom(v)
            }
          }
        }
        for ((encoding, actions) <- normalEncodings) decodeInstruction(encoding, actions)
        // priority升序，when语句后面的覆盖前面的
        for (encoding <- encodingsByPriority) decodeInstruction(encoding, encodings(encoding))

        import MicroOpSignals._
        // PC
        uop.pc := entry.pc
        uop.predInfo := entry.predInfo
        uop.predRecover := entry.predRecover
        uop.inst := entry.inst
        val wbType = local(wbSel)
        uop.wbAddr.assignDontCare()
        uop.wbAddr.swichAssign(wbType)(
          RegWriteAddr.RT -> fields.rt,
          RegWriteAddr.RD -> fields.rd,
          RegWriteAddr.R31 -> 31
        )
        // 写0处理成不写
        uop.doRegWrite := wbType =/= RegWriteAddr.NONE && uop.wbAddr =/= 0
        // decode异常
        uop.except := entry.except
        when(!entry.except.valid) {
          uop.except.payload.isTLBRefill := False
          // 增加执行类异常：syscall, breakpoint
          when(local(isSyscall)) {
            uop.except.valid := True
            uop.except.payload.code := ExceptionCode.syscall
          }
          when(local(isBreak)) {
            uop.except.valid := True
            uop.except.payload.code := ExceptionCode.breakpoint
          }
          // 增加reserved instruction异常
          when(illegalEncoding) {
            uop.except.valid := True
            uop.except.payload.code := ExceptionCode.reservedInstruction
          }
          // 增加cop0 unusable异常
          val cop0Inst = fields.opcode === B(MIPS32.COP0) || instruction === MIPS32.CACHE
          when(
            cop0Inst &&
              excHandler.privMode =/= PrivMode.KERNEL &&
              !excHandler.statusCU0
          ) {
            uop.except.valid := True
            uop.except.payload.code := ExceptionCode.coprocessorUnusable
          }
        }
        uop.branchLike := uop.isBranch || uop.isJump || uop.isJR
        // 处理unique retire类指令
        uop.flushState := uop.isEret || uop.isWait || uop.writeCP0 || uop.isCondMove || uop.operateTLB || uop.operateCache
        // flushState是uniqueRetire的子集
        if (config.decode.allUnique) {
          println("[Warning] All Unique retire enabled")
          uop.uniqueRetire := True
        } else {
          uop.uniqueRetire := uop.flushState ||
            uop.branchLike ||
            uop.isLoad ||
            uop.isStore ||
            uop.predInfo.predictBranch
        }
      }

    // 生成decoder阵列
    val decoders = for (i <- 0 until width) yield buildSingleDecoder(popPorts(i).payload)
    val decodePacket = insert(pipeline.signals.DECODE_PACKET)
    for (i <- 0 until width) {
      decodePacket(i).valid := popPorts(i).valid
      decodePacket(i).payload := decoders(i).uop
      popPorts(i).ready := arbitration.isFiring
    }

  }
}
