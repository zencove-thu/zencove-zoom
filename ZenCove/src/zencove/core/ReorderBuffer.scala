package zencove.core

import zencove.builder.Plugin
import zencove.model._
import spinal.core._
import spinal.lib._
import zencove.ZencoveConfig
import zencove.enum._
import zencove.util._
import scala.math
import spinal.lib.fsm._
import scala.collection.mutable.ArrayBuffer

class ReorderBuffer(config: ZencoveConfig)
    extends Plugin[CPU]
    with ExceptionCommit
    with TLBCommit
    with CacheCommit
    with BPUCommit
    with CP0Commit
    with ARFCommit
    with WaitCommit
    with StoreBufferCommit
    with CommitFlush {
  val predUpdate = Flow(PredictUpdate(config.frontend))

  private val retireWidth = config.rob.retireWidth
  private val decodeWidth = config.decode.decodeWidth

  // 向外的提交接口
  override val arfCommits = Vec(Flow(RegFileMappingEntry(config.regFile)), retireWidth)
  override val hiLoCommits = Vec(Bool, retireWidth)
  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    val robFIFO = pipeline.service[ROBFIFO]
    val jumpInterface = pipeline.pFetch.service[ProgramCounter].backendJumpInterface
    pipeline.RENAME plug new Area {
      import pipeline.RENAME._
      // dispatch to ROB at RENAME stage
      val decPacket = input(pipeline.pDecode.signals.DECODE_PACKET)
      val renameRecs = input(pipeline.pDecode.signals.RENAME_RECORDS)
      val robIdxs = insert(pipeline.pDecode.signals.ROB_INDEXES)
      val pushPorts = robFIFO.fifoIO.push
      for (i <- 0 until decodeWidth) {
        val valid = decPacket(i).valid
        val uop = decPacket(i).payload
        val rename = renameRecs(i)
        val port = pushPorts(i)
        val entry = port.payload
        // 自动complete包括不需要执行的指令和前端异常发生
        entry.state.complete := uop.needNotExecute
        entry.state.except.assignSomeByName(uop.except)
        entry.state.mispredict := !uop.branchLike && uop.predInfo.predictTaken
        // entry.state.lsuUncached := False
        entry.state.condTrue := True
        entry.state.actualTaken := False
        entry.info.rename.assignAllByName(rename)
        entry.info.uop.assignSomeByName(uop)
        entry.info.uop.writeHiLo := uop.writeHiLo
        val fields = InstructionParser(uop.inst)
        entry.info.uop.neededInstField.assignDontCare()
        when(uop.writeCP0) {
          entry.info.uop.neededInstField := fields.cp0Addr.asBits
        } elsewhen (uop.operateCache) {
          entry.info.uop.neededInstField(4 downto 0) := fields.rt.asBits
        } otherwise {
          entry.info.uop.neededInstField(4 downto 0) := fields.rs.asBits
        }
        entry.info.frontendExc := uop.except.valid
        port.valid := arbitration.isValidNotStuck && valid
        arbitration.haltItself setWhen (arbitration.isValid && valid && !port.ready)
        robIdxs(i) := robFIFO.pushPtr + i
      }
    }
    val retire = new Area {
      // retire asynchronously pops from ROB
      val popPorts = robFIFO.fifoIO.pop

      // default values
      tlbOp := TLBOp.NONE
      cacheOp.setIdle()
      predUpdate.setIdle()
      cp0Write.setIdle()
      arfCommits.foreach(_.setIdle())
      hiLoCommits.foreach(_ := False)
      doWait := False
      eret := False
      commitStore := False
      waitDelaySlot := False

      // 所有mask相与得到最终可以retire的指令
      val completeMask = B((0 until retireWidth).map { i =>
        // 左侧包括自己都complete
        popPorts.take(i + 1).map(_.state.complete).andR
      })
      val excMask = Bits(retireWidth bits) // exception
      val uniqueMask = Bits(retireWidth bits) // unique retire
      val recoverMask = Bits(retireWidth bits) // mispredict recover
      val uncachedMask = Bits(retireWidth bits) // uncached load/store
      excMask(0) := True
      uniqueMask(0) := True
      recoverMask(0) := True
      val readyMask = completeMask & excMask & uniqueMask & recoverMask & uncachedMask

      val hasExcept = Bool
      val condTrue = if (config.isa.useCondMove) Bool else True
      inDelaySlot := RegNextWhen(waitDelaySlot, popPorts(0).fire, init = False)
      val delaySlotDetached = False
      val inDetachedDelaySlot = RegNextWhen(delaySlotDetached, popPorts(0).fire, init = False)
      val linearRecover = Bool

      val port0Commit = new Area {
        // port0特殊处理
        val port = popPorts(0)
        val entry = port.payload
        val uop = port.info.uop
        val fire = port.fire
        val rsField = uop.neededInstField(4 downto 0)
        val cp0AddrField = uop.neededInstField.asUInt
        val cacheSelField = CacheSel()
        cacheSelField.assignFromBits(uop.neededInstField(1 downto 0))
        val cacheOpField = CacheOp()
        cacheOpField.assignFromBits(uop.neededInstField(4 downto 2))

        // conditional move
        if (config.isa.useCondMove) condTrue := entry.state.condTrue

        // 中断处理：中断被当作exception提交，则自然屏蔽所有指令性提交
        // exception分类在handler中进行，若本身就有exception，中断会被优先处理
        val intPending = pipeline.service[IntStatusProvider].intPending
        val intInhibit = False // 用于在uncached开始执行后屏蔽中断的提交
        hasExcept := entry.state.except.valid || (intPending && !intInhibit)

        val mispredict = entry.state.mispredict
        // 需要flush到PC+4的情况：1. 指令本身改变影响处理器状态；2. 非branch被预测改变了控制流
        linearRecover := uop.flushState || (!uop.branchLike && mispredict)
        // 需要回滚处理器状态的情况：
        // 1. 分支：预测错误
        // 2. 异常
        // 3. 其它提交后需要flush流水线的指令
        val recoverState = fire && (hasExcept || mispredict || linearRecover)
        // 如果不需要等delay slot，则立即flush；否则等到delay slot发射时flush
        needFlush := (recoverState && !waitDelaySlot) || (inDelaySlot && fire)
        robFIFO.fifoIO.flush := needFlush // flush周期同时pop
        // mispredict仍然会提交分支指令和delay slot，所以要先更新ARF，再回滚PRF
        // flush的下一个周期不会pop，所以没有关系
        recoverPRF := regFlush
        val jumpTarget = U(0, 32 bits)
        val saveJumpTarget =
          RegNextWhen(jumpTarget, popPorts(0).fire & delaySlotDetached, init = U(0, 32 bits))

        // clear frontend pipelines
        pipeline.pFetch.stages.last.arbitration.flushIt setWhen needFlush
        pipeline.pDecode.stages.last.arbitration.flushIt setWhen needFlush

        // exception永远从0口unique发出
        except.valid := fire && hasExcept
        except.payload := entry.state.except.payload
        // 前端异常的badVA必然是pc
        when(entry.info.frontendExc) { except.badVA := entry.info.uop.pc }
        epc := uop.pc
        exceptInDelaySlot := inDetachedDelaySlot
        val storeBuffer = pipeline.pMemory.service[StoreBuffer]
        when(fire & !hasExcept) {
          // 所有指令性的commit需要在没有except的时候发出
          tlbOp := uop.tlbOp
          cacheOp.valid := uop.operateCache
          // 复用badVA，如果没有异常的时候badVA填cache需要的物理地址
          cacheOp.payload.addr := entry.state.except.badVA
          cacheOp.payload.sel := cacheSelField
          cacheOp.payload.op := cacheOpField
          cp0Write.valid := uop.writeCP0
          cp0Write.payload.addr := cp0AddrField
          // intResult装填读rt的结果
          cp0Write.payload.data := entry.state.intResult.asBits
          doWait := uop.isWait
          eret := uop.isEret

          // 分支本来需要回滚，但要等等delay slot先提交完成。注意非branch mispredict由于刷到PC+4，无delay slot。
          delaySlotDetached := uop.branchLike && !popPorts(1).fire
          waitDelaySlot := delaySlotDetached && mispredict

          //any of these situation should trigger possible btb update.
          predUpdate.valid := (if (config.frontend.btb.enable)
                                 uop.branchLike || uop.predInfo.predictBranch
                               else False)
          predUpdate.payload.predInfo := uop.predInfo
          predUpdate.payload.predRecover := uop.predRecover
          predUpdate.payload.branchLike := uop.isBranch || uop.isJump || (uop.isJR && rsField === 31) //jump is a always true branch for btb
          // returns also needed to record in btb for ras to predict correctly.
          predUpdate.payload.isTaken := (entry.state.mispredict ^ uop.predInfo.predictTaken) || uop.isJump || uop.isJR //note that jump and jr is always taken...
          // 'jr ra' 'jalr ra' as return
          predUpdate.payload.isRet := uop.isJR && rsField === 31
          // all jump/branch with link can be call
          predUpdate.payload.isCall := uop.branchLike && uop.doRegWrite
          predUpdate.payload.mispredict := entry.state.mispredict
          predUpdate.payload.pc := uop.pc
          predUpdate.payload.target := entry.state.intResult

          when(uop.isJump || uop.isJR) {
            jumpTarget := entry.state.intResult //when is Jump, go to actual target
          } otherwise {
            jumpTarget := Mux(
              entry.state.actualTaken,
              entry.state.intResult,
              uop.pc + 8
            ) //when branch, decide recover or not based on predictTaken
          }

          when(linearRecover && !uop.isEret) {
            // 这些是本身不改变控制流，但因为flush而需要改变控制流
            jumpInterface.valid := True
            // 若此时在delay slot中，则实际target由保存的分支真实目标决定，否则就是PC+4
            jumpInterface.payload := Mux(inDetachedDelaySlot, saveJumpTarget, uop.pc + 4)
          }
          when(entry.state.mispredict && !waitDelaySlot && uop.branchLike) {
            // 分支预测错误且delay slot一起提交
            jumpInterface.valid := True
            jumpInterface.payload := jumpTarget
          }
          when(inDelaySlot) {
            // 单独提交delay slot
            jumpInterface.valid := True
            jumpInterface.payload := saveJumpTarget
          }
          // 以上优先级是重要的。Branch本身的跳转目标优先于delay slot中的mispredict（一定非branch）。
          commitStore := uop.isStore
        }
        val uncachedProcess = new Area {
          // 处理uncached load的提交问题
          // uncached load要等待store buffer将其发射并执行到WB阶段才能提交
          // 避免出现寄存器已经被重分配出去的问题
          val isUncachedUOP = uop.isLoad && entry.state.lsuUncached
          val fsm = new StateMachine {
            disableAutoStart()
            setEntry(stateBoot)
            val execute = new State

            uncachedMask.setAll()

            stateBoot.whenIsActive {
              when(port.valid && isUncachedUOP && entry.state.complete && !hasExcept) {
                // 不提交，等待store buffer执行
                uncachedMask := 0
                commitStore := True
                goto(execute)
              }
            }
            execute.whenIsActive {
              intInhibit := True
              uncachedMask := 0
              val memWB = pipeline.pMemory.WB
              val wbSTD = memWB.input(pipeline.pMemory.signals.STD_SLOT)
              val isUncachedWB = wbSTD.valid && !wbSTD.isStore
              // uncached load执行完成
              when(memWB.arbitration.notStuck && isUncachedWB) {
                // FIXME: 需要保证此周期一定提交且不触发异常
                uncachedMask.setAll()
                goto(stateBoot)
              }
            }
          }
        }
      }

      for (i <- 1 until retireWidth) {
        val port = popPorts(i)
        // 左侧包括自己没有非delay slot控制流转移，可以commit
        excMask(i) := !popPorts
          .take(i + 1)
          .map { p => p.state.except.valid || linearRecover }
          .orR
        // 1~i没有要求在0口commit的指令
        // 这里unique retire只要求在0口commit，并不影响其它非unique指令在后面commit，
        // 因此控制流指令不能只依赖unique retire
        // load指令包含在了unique retire中，因此不需要另外判断uncached了
        uniqueMask(i) := !popPorts
          .slice(1, i + 1)
          .map(p => p.info.uop.uniqueRetire)
          .orR
        recoverMask(i) := {
          // 1. i=1时，本条delay slot允许一起提交
          // 2. 0~i mispredict，均非可提交的情况
          if (i == 1) !port.state.mispredict
          else !popPorts.take(i + 1).map(_.state.mispredict).orR
        } && !inDelaySlot // inDelaySlot说明现在等待0口delay slot提交后回滚状态，不能commit
      }

      val busyAfterCommitTrigger = RegInit(False)

      for (i <- 0 until retireWidth) {
        val port = popPorts(i)
        val uop = port.info.uop
        val rename = port.info.rename
        port.ready := readyMask(i)
        when(port.fire & !hasExcept & condTrue) {
          // 所有指令性的commit需要在没有except的时候发出
          arfCommits(i).valid := uop.doRegWrite
          arfCommits(i).payload.addr := uop.wbAddr
          arfCommits(i).payload.prevAddr := rename.wPrevReg
          arfCommits(i).payload.prfAddr := rename.wReg
          hiLoCommits(i) := uop.writeHiLo
        }
      }
    }
  }
}
