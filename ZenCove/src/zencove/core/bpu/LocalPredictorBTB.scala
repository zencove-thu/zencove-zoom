package zencove.core.bpu

import zencove.builder._
import spinal.core._
import zencove.model.BranchTableEntry
import zencove.util._
import spinal.lib._
import zencove.enum._
import zencove.core._
import zencove.FrontendConfig

class LocalPredictorBTB(config: FrontendConfig) extends Plugin[FetchPipeline] with BTB {
  val numEntries = config.btb.sets
  val fetchWidth = config.fetchWidth
  val entriesLg2 = config.btb.indexWidth
  val btbConfig = config.btb

  val valid = Vec(RegInit(False), numEntries)
  val btb = new ReorderCacheRAM(BranchTableEntry(config.btb), numEntries, fetchWidth, false)
  // val predictor = new CorrelatingPredictor(config.bpu, fetchWidth)
  val historyTable = new TwoLayerHistoryTable(config.bpu, fetchWidth)
  val predictTable = new TwoLayerPredictTable(config.bpu, fetchWidth)

  val predictInterface = Flow(UWord())
  val fetchDelaySlot = RegInit(False)
  val jumpTarget = Reg(UWord())

  object BTB_RESP extends Stageable(Vec(BranchTableEntry(config.btb), fetchWidth))

  override def setup(pipeline: FetchPipeline): Unit = {
    val programCounter = pipeline.service[ProgramCounter]
    programCounter.setPredict(predictInterface)
  }

  override def build(pipeline: FetchPipeline): Unit = {

    // connect history table and predict table
    predictTable.io.read.histories := historyTable.io.read.histories

    import pipeline.IF1
    import pipeline.IF2
    pipeline plug new Area {
      val nextPC = pipeline.service[ProgramCounter].nextPC
      val index = nextPC(btbConfig.indexRange)
      btb.io.read.cmd.valid := !IF1.arbitration.isStuck
      btb.io.read.cmd.payload := index
      // predictor.io.read.nextPC := nextPC
      historyTable.io.read.nextPC := nextPC
      historyTable.io.read.enable := IF1.arbitration.notStuck
      predictTable.io.read.enable := !IF2.arbitration.isStuck
    }

    IF1 plug new Area {
      import IF1._
      import pipeline.signals._
      val tag = input(PC)(btbConfig.tagRange)
      val index = input(PC)(btbConfig.indexRange)
      val fetchWay = input(PC)(config.icache.wordOffsetRange)

      predictTable.io.read.pc := input(PC)

      // Transfer all status bundles for further use in IF2
      val hitData = btb.io.read.rsp
      insert(BTB_RESP) := hitData

      //default with no prediction
      insert(BRANCH_MASK).clearAll
      insert(PRIVATE_BRANCH_HISTORY) := historyTable.io.read.histories

      for (i <- fetchWidth - 1 downto 0) { //priority logic
        val way = fetchWay + i
        when(way >= fetchWay) { //confirm not out of bound
          val entry = hitData(i)
          val validFlag = valid(index + i) && entry.tag === tag
          when(validFlag) { //matched entry
            insert(BRANCH_MASK)(i) := True // set branch mask
          }
        }
      }
    }

    import pipeline.IF2
    IF2 plug new Area {
      //predict logic
      import IF2._
      import pipeline.signals._

      insert(PREDICT_JUMP_FLAG) := False
      insert(PREDICT_JUMP_PAYLOAD) := input(BTB_RESP)(0).statusBundle
      insert(PREDICT_JUMP_WAY) := 0

      insert(INSTRUCTION_MASK).setAll
      insert(PRED_COUNTER) := predictTable.io.read.predCounters

      for (i <- fetchWidth - 1 downto 0) {
        when(input(BRANCH_MASK)(i)) {
          when(predictTable.io.read.predCounters(i) >= 2) { //predict taken
              insert(PREDICT_JUMP_FLAG) := True
              insert(PREDICT_JUMP_PAYLOAD) := input(BTB_RESP)(i).statusBundle
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

      val tag = input(PC)(btbConfig.tagRange)
      val index = input(PC)(btbConfig.indexRange)
      val fetchWay = input(PC)(config.icache.wordOffsetRange)

      val jumpFlag = insert(PREDICT_JUMP_FLAG)
      val jumpPayload = insert(PREDICT_JUMP_PAYLOAD)
      val jumpWay = insert(PREDICT_JUMP_WAY)

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
    }

    pipeline plug new Area {
      // commit area
      val bpuCommit = pipeline.globalService[BPUCommit]
      val predUpdate = bpuCommit.predUpdate

      btb.io.write.setIdle()
      // predictor.io.write.setIdle()
      predictTable.io.write.setIdle()
      historyTable.io.write.setIdle()

      when(predUpdate.valid) { // time to update
        val payload = predUpdate.payload
        val pred = payload.predInfo
        val recover = payload.predRecover
        val tag = payload.pc(btbConfig.tagRange)
        val index = payload.pc(btbConfig.indexRange)

        btb.io.write.payload.address := index
        btb.io.write.payload.mask := B"1".resized
        historyTable.io.write.payload.pc := payload.pc

        when(pred.predictBranch && !payload.branchLike) {
          //Case 1, modified instruction, not branch anymore, must be mispredicted
          valid(index) := False //invalidate the entry
        } elsewhen (!pred.predictBranch && payload.branchLike) {
          //Case 2, new entry, must be mispredicted
          val newEntry = BranchTableEntry(config.btb)
          newEntry.tag := tag
          newEntry.statusBundle.isCall := payload.isCall
          newEntry.statusBundle.isReturn := payload.isRet
          historyTable.io.write.payload.newHistory := U(0, config.bpu.historyWidth - 1 bits) @@ payload.isTaken
          newEntry.statusBundle.target := payload.target(31 downto 2)
          btb.io.write.payload.data(0) := newEntry
          btb.io.write.valid := True
          historyTable.io.write.valid := True
          valid(index) := True
        } elsewhen (pred.predictBranch && payload.branchLike) { //update the entry
          val newEntry = BranchTableEntry(config.btb)
          newEntry.tag := tag
          newEntry.statusBundle.target := payload.target(31 downto 2)
          newEntry.statusBundle.isCall := payload.isCall
          newEntry.statusBundle.isReturn := payload.isRet

          historyTable.io.write.payload.newHistory := recover.ghr(config.bpu.historyWidth - 2 downto 0) @@ payload.isTaken
          historyTable.io.write.valid := True

          predictTable.io.write.payload.history := recover.ghr
          predictTable.io.write.payload.pc := predUpdate.payload.pc
          when(payload.isTaken && recover.predictCounter =/= 3) {
            predictTable.io.write.newCounter := recover.predictCounter + 1
          } elsewhen (!payload.isTaken && recover.predictCounter =/= 0) {
            predictTable.io.write.newCounter := recover.predictCounter - 1
          } otherwise {
            predictTable.io.write.newCounter := recover.predictCounter
          }

          btb.io.write.payload.data(0) := newEntry
          btb.io.write.valid := True
          predictTable.io.write.valid := True
        }
      }
    }
  }
}
