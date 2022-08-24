package zencove.core

import spinal.core._
import zencove.ZencoveConfig
import spinal.lib.master
import zencove.builder._
import zencove.model.DebugInterface
import scala.collection.mutable.ArrayBuffer
import zencove.util._

class CPUCore(config: ZencoveConfig) extends Component with CPU {
  override val signals = new CPUSignals(config)
  override val pFetch: FetchPipeline = new FetchPipeline {
    override val signals = new FetchSignals(config)
    override val IF1: Stage = newStage()
    override val IF2: Stage = newStage()
    plugins ++= List(
      new bpu.GlobalPredictorBTB(config.frontend),
      new ExceptionMux[FetchPipeline](stages.size - 1),
      new FetchBuffer(config),
      new InstAddrTranslate(config),
      config.frontend.icache.useReorder generate new ICache(config),
      !config.frontend.icache.useReorder generate new ICacheNoReorder(config),
      new bpu.RAS(config.frontend),
      new ProgramCounter(config.frontend)
    ).filter(_ != null)
  }
  override val pDecode: DecodePipeline = new DecodePipeline {
    override val signals = new DecodeSignals(config)
    override val ID: Stage = newStage()
    override val RENAME: Stage = newStage()
    override val DISPATCH: Stage = newStage()
    plugins ++= List(
      new DecoderArray(config, pFetch.service[FetchBuffer].popPorts),
      new MIPS32Decode(config.isa),
      new RenameByPRF(config),
      new RenameHiLo(config)
    ).filter(_ != null)
  }
  override val pMemory: MemPipeline = new Area with MemPipeline {
    override val signals: MemSignals = new MemSignals(config)
    override val ISS: Stage = newStage()
    override val RRD: Stage = newStage()
    override val MEM1: Stage = newStage()
    override val MEM2: Stage = newStage()
    override val WB: Stage = newStage()
    override val WB2: Stage = newStage()
    plugins ++= List(
      new AGU(config),
      new DCacheWriteBack(config),
      new ExceptionMux[MemPipeline](stages.size - 1),
      new LoadPostprocess,
      new MemExecute(config),
      new StoreBuffer(config),
      new UncachedAccess(config)
    ).filter(_ != null)
  }
  val pMulDiv = new Area with ExecutePipeline {
    override val ISS: Stage = newStage()
    override val RRD: Stage = newStage()
    override val EXE: Stage = newStage()
    override val WB: Stage = newStage()
    plugins += new MulDivExecute(config)
  }
  override val IF1: Stage = pFetch.IF1
  override val IF2: Stage = pFetch.IF2
  override val ID: Stage = pDecode.ID
  override val RENAME: Stage = pDecode.RENAME
  override val DISPATCH: Stage = pDecode.DISPATCH
  addPipeline(pFetch)
  addPipeline(pDecode)
  addPipeline(pMemory)
  addPipeline(pMulDiv)

  for (i <- (0 until config.intIssue.issueWidth).reverse) {
    val exePipeline = new ExecutePipeline {
      override val ISS: Stage = newStage().setName(s"INT${i}_ISS")
      override val RRD: Stage = newStage().setName(s"INT${i}_RRD")
      override val EXE: Stage = newStage().setName(s"INT${i}_EXE")
      override val WB: Stage = newStage().setName(s"INT${i}_WB")
      plugins += new IntExecute(config, i)
    }
    addPipeline(exePipeline)
  }
  plugins ++= List(
    new BypassNetwork(config.regFile), // ALU bypass network
    new ConfigRegisters(config),
    new Counter(config.interrupt),
    new CP0Regs(),
    new ExceptionHandler(),
    new HiLoRegFile(config.hlu),
    new IntIssueQueue(config),
    new InterruptHandler(config.interrupt),
    new MemIssueQueue(config),
    new MulDivIssueQueue(config),
    new PhysRegFile(config.regFile),
    new ReorderBuffer(config),
    new ROBFIFO(config),
    config.useSpeculativeWakeup generate new SpeculativeWakeupHandler(),
    new TLB(config.tlb),
    config.isa.useWAIT generate new WaitHandler
  ).filter(_ != null)
  val io = new Bundle {
    val iBus = master(pFetch.service[IBus].iBus.toAxi4())
    val dBus = master(pMemory.service[DBus].dBus)
    val udBus = master(pMemory.service[UDBus].udBus)
    val extInt = in(Bits(6 bits)) default 0
    val debug = config.debug generate out(DebugInterface())
  }
  service[InterruptHandler].extInt := io.extInt
  if (config.debug) {
    val ROB = service[ROBFIFO]
    io.debug.wb.pc := ROB.robInfo.io.pop(0).payload.uop.pc
    // io.debug.wb.rf.wen.setAllTo(ROB.robInfo.io.pop(0).payload.uop.doRegWrite)
    io.debug.wb.rf.wen := 0
    io.debug.wb.rf.wnum := ROB.robInfo.io.pop(0).payload.uop.wbAddr
    io.debug.wb.rf.wdata.assignDontCare()
  }

  afterElaboration {
    noIoPrefix()
    prunePayloadRecursive(io)
    Axi4Naming.setAllAXIName(io)
  }
}
