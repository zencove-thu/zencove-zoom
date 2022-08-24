package zencove.core

import spinal.core._
import spinal.lib._
import zencove.model._
import zencove.enum._
import zencove.util._

trait ExceptionCommit {
  val except = Flow(ExceptionPayload(true))
  val eret = Bool
  val epc = UWord()
  val exceptInDelaySlot = Bool
}

trait TLBCommit {
  val tlbOp = TLBOp()
}

trait CacheCommit {
  val cacheOp = Flow(CacheOperation())
}

/** Commit to branch prediction unit.
  */
trait BPUCommit {
  val predUpdate: Flow[PredictUpdate]
}

trait CP0Commit {
  val cp0Write = Flow(CP0Write())
}

trait ARFCommit {
  val arfCommits: Vec[Flow[RegFileMappingEntry]]
  val hiLoCommits: Vec[Bool]
  // 将PRF恢复为当前architecutre state
  val recoverPRF = Bool
}

trait WaitCommit {
  val doWait = Bool
}

/** Commit to store buffer.
  */
trait StoreBufferCommit {
  val commitStore = Bool
}

/** Commit to LSU (commit uncached load/store)
  */
trait LSUCommit {
  val uncachedSlot: Stream[MemIssueSlot]
}

trait CommitFlush {
  val needFlush = Bool
  val regFlush = RegNext(needFlush, init = False)
  val waitDelaySlot = Bool
  val inDelaySlot = Bool
}
