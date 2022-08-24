package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model._
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.core._
import zencove.FrontendConfig

class HybridPredictorBTB(config: FrontendConfig) extends Plugin[FetchPipeline] with BTB {
  val numEntries = config.btb.sets
  val fetchWidth = config.fetchWidth
  val entriesLg2 = config.btb.indexWidth
  val btbConfig = config.btb

  val valid = Vec(RegInit(False), numEntries)
  val btb = new ReorderCacheRAM(BranchTableEntry(config.btb), numEntries, fetchWidth, false)
  val predictor = new HybridPredictor(config, fetchWidth)

  val predictInterface = Flow(UWord())
  val fetchDelaySlot = RegInit(False)
  val jumpTarget = Reg(UWord())

  override def setup(pipeline: FetchPipeline): Unit = {
    val programCounter = pipeline.service[ProgramCounter]
    programCounter.setPredict(predictInterface)
  }

  override def build(pipeline: FetchPipeline): Unit = {

    import pipeline.IF1
    pipeline plug new Area {
      val nextPC = pipeline.service[ProgramCounter].nextPC
      val index = nextPC(btbConfig.indexRange)
      btb.io.read.cmd.valid := !IF1.arbitration.isStuck
      btb.io.read.cmd.payload := index
      predictor.io.read.enable := IF1.arbitration.notStuck
      predictor.io.read.nextPC := nextPC
    }

    IF1 plug new Area {
      import IF1._
      import pipeline.signals._
      val tag = input(PC)(btbConfig.tagRange)
      val index = input(PC)(btbConfig.indexRange)
      val fetchWay = input(PC)(config.icache.wordOffsetRange)

      val hitData = btb.io.read.rsp

      insert(PREDICT_JUMP_FLAG) := False
      insert(PREDICT_JUMP_PAYLOAD) := hitData(0).statusBundle
      insert(PREDICT_JUMP_WAY) := 0

      //default with no prediction
      insert(INSTRUCTION_MASK).setAll
      insert(BRANCH_MASK).clearAll

      for (i <- fetchWidth - 1 downto 0) { //priority logic
        val way = fetchWay + i
        when(way >= fetchWay) { //confirm not out of bound
          val entry = hitData(i)
          val validFlag = valid(index + i) && entry.tag === tag
          when(validFlag) { //matched entry
            insert(BRANCH_MASK)(i) := True // set branch mask
            when(predictor.io.read.predTaken(i)) { //predict taken
              insert(PREDICT_JUMP_FLAG) := True
              insert(PREDICT_JUMP_PAYLOAD) := entry.statusBundle
              insert(PREDICT_JUMP_WAY) := i
              if (i != fetchWidth - 1) {
                insert(INSTRUCTION_MASK) := (1 << (i + 2)) - 1 //set instruction mask
                // for example, when i=1 is jump, 0,1 are common instructions
                // 2 is delay slot, so mask should be 0...0111 = 2^(1+2) - 1
                // if i = fetchwidth - 1, then all instructions are legal
              }
            }
          }
        }
      }

      insert(HYBRID_RECOVERS) := predictor.io.read.predInfo

      // inDelaySlot时暂停取指，避免污染预测器
      arbitration.removeIt setWhen (arbitration.notStuck &&
        pipeline.globalService[CommitFlush].inDelaySlot)
    }

    import pipeline.IF2
    IF2 plug new Area {
      //predict logic
      import IF2._

      val tag = input(PC)(btbConfig.tagRange)
      val index = input(PC)(btbConfig.indexRange)
      val fetchWay = input(PC)(config.icache.wordOffsetRange)

      import pipeline.signals._
      val jumpFlag = input(PREDICT_JUMP_FLAG)
      val jumpPayload = input(PREDICT_JUMP_PAYLOAD)
      val jumpWay = input(PREDICT_JUMP_WAY)

      val lastValidWay = U(fetchWidth - 1)
      for (i <- fetchWidth - 1 downto 1) {
        when(!input(FETCH_PACKET).insts(i).valid) { lastValidWay := i - 1 }
      }

      val payloadTarget = jumpPayload.target @@ U(0, 2 bits)
      insert(PREDICT_ADDR) := payloadTarget
      val rasPredict = pipeline.service[RAS].rasPredict
      when(rasPredict.valid) { payloadTarget := rasPredict.payload }

      predictInterface.payload := payloadTarget
      predictInterface.valid := False

      insert(TAKEN_MASK).clearAll

      when(arbitration.isValid) {
        when(fetchDelaySlot) {
          predictInterface.valid := True
          predictInterface.payload := jumpTarget

          // only instruction 0 valid, which is in delay slot
          input(INSTRUCTION_MASK) := 1
          fetchDelaySlot clearWhen arbitration.isFiring

          IF2.arbitration.flushNext := True
        } elsewhen (jumpFlag) {
          insert(TAKEN_MASK)(jumpWay) := True
          when(jumpWay === lastValidWay) { //need extra fetch for delay slot
            fetchDelaySlot setWhen arbitration.isFiring
            jumpTarget := payloadTarget
          } otherwise {
            predictInterface.valid := True
            IF2.arbitration.flushNext := True
            //interface payload has already been set.
          }
        }
      }
      fetchDelaySlot clearWhen arbitration.isFlushed

      val branchCount = CountOne(input(INSTRUCTION_MASK) & input(BRANCH_MASK))
      val ghr = input(HYBRID_RECOVERS)(0).history
      val shiftedGHR = ghr |<< branchCount
      predictor.io.updateGHR.setIdle()
      when(arbitration.isFiring) {
        predictor.io.updateGHR.push(shiftedGHR | input(PREDICT_JUMP_FLAG).asUInt.resized)
      }
    }

    pipeline plug new Area {
      // commit area
      val bpuCommit = pipeline.globalService[BPUCommit]
      val predUpdate = bpuCommit.predUpdate

      btb.io.write.setIdle()
      predictor.io.write.setIdle()

      when(predUpdate.valid) { // time to update
        val payload = predUpdate.payload
        val pred = payload.predInfo
        val recover = payload.predRecover
        val tag = payload.pc(btbConfig.tagRange)
        val index = payload.pc(btbConfig.indexRange)

        btb.io.write.payload.address := index
        btb.io.write.payload.mask := B"1".resized
        predictor.io.write.pc := payload.pc
        predictor.io.write.newInfo := recover.hybridRecover

        when(pred.predictBranch && !payload.branchLike) {
          //Case 1, modified instruction, not branch anymore, must be mispredicted
          valid(index) := False //invalidate the entry
        } elsewhen (!pred.predictBranch && payload.branchLike) {
          //Case 2, new entry, must be mispredicted
          val newEntry = BranchTableEntry(config.btb)
          newEntry.tag := tag
          newEntry.statusBundle.isCall := payload.isCall
          newEntry.statusBundle.isReturn := payload.isRet
          // TODO: try to parameterize these
          when(payload.isCall || payload.isRet) {
            predictor.io.write.newInfo.localCounter := 3
          } otherwise {
            predictor.io.write.newInfo.localCounter := Mux(
              payload.isTaken,
              U(2, 2 bits),
              U(1, 2 bits)
            )
            predictor.io.write.newInfo.globalCounter := Mux(
              payload.isTaken,
              U(2, 2 bits),
              U(1, 2 bits)
            )
          }
          newEntry.statusBundle.target := payload.target(31 downto 2)
          btb.io.write.payload.data(0) := newEntry
          btb.io.write.valid := True
          predictor.io.write.valid := True
          valid(index) := True
        } elsewhen (pred.predictBranch && payload.branchLike) { //update the entry
          val newEntry = BranchTableEntry(config.btb)
          newEntry.tag := tag
          newEntry.statusBundle.target := payload.target(31 downto 2)
          newEntry.statusBundle.isCall := payload.isCall
          newEntry.statusBundle.isReturn := payload.isRet

          // update 3 counters
          when(payload.isTaken && recover.hybridRecover.localCounter =/= 3) {
            predictor.io.write.newInfo.localCounter := recover.hybridRecover.localCounter + 1
          } elsewhen (!payload.isTaken && recover.hybridRecover.localCounter =/= 0) {
            predictor.io.write.newInfo.localCounter := recover.hybridRecover.localCounter - 1
          } otherwise {
            predictor.io.write.newInfo.localCounter := recover.hybridRecover.localCounter
          }
          when(payload.isTaken && recover.hybridRecover.globalCounter =/= 3) {
            predictor.io.write.newInfo.globalCounter := recover.hybridRecover.globalCounter + 1
          } elsewhen (!payload.isTaken && recover.hybridRecover.globalCounter =/= 0) {
            predictor.io.write.newInfo.globalCounter := recover.hybridRecover.globalCounter - 1
          } otherwise {
            predictor.io.write.newInfo.globalCounter := recover.hybridRecover.globalCounter
          }
          val localMispredict = payload.isTaken ^ recover.hybridRecover.localCounter.msb
          val globalMispredict = payload.isTaken ^ recover.hybridRecover.globalCounter.msb
          when(localMispredict && !globalMispredict && recover.hybridRecover.selectCounter =/= 3) {
            predictor.io.write.newInfo.selectCounter := recover.hybridRecover.selectCounter + 1
          } elsewhen (!localMispredict && globalMispredict && recover.hybridRecover.selectCounter =/= 0) {
            predictor.io.write.newInfo.selectCounter := recover.hybridRecover.selectCounter - 1
          } otherwise {
            predictor.io.write.newInfo.selectCounter := recover.hybridRecover.selectCounter
          }
          btb.io.write.payload.data(0) := newEntry
          btb.io.write.valid := True
          predictor.io.write.valid := True
        }

        when(payload.mispredict) {
          // restore GHR
          predictor.io.updateGHR.push((recover.hybridRecover.history @@ payload.isTaken).resized)
        }
      }
    }
  }
}
