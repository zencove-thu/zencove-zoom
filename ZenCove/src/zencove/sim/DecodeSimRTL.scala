package zencove.sim

import zencove.ZencoveConfig
import spinal.core._
import spinal.lib._
import zencove.core._
import zencove.builder._
import scala.reflect.io.Directory
import zencove.enum._
import zencove.model._

class DefaultStatusProvider extends Plugin[CPU] with StatusProvider {
  val privMode = PrivMode.KERNEL()
  val statusCU0 = False
  val statusERL = False
  val statusEXL = False
  val statusBEV = True
  override def build(pipeline: CPU): Unit = {}
}

class DecodeSimRTL(config: ZencoveConfig) extends Component with CPU {
  val io = new Bundle {
    val fetchBufferPops = Vec(slave(Stream(InstBufferEntry(config.frontend))), config.decode.decodeWidth)
  }
  override val signals = new CPUSignals(config)
  override val pDecode: DecodePipeline = new DecodePipeline {
    override val signals = new DecodeSignals(config)
    override val ID: Stage = newStage()
    override val RENAME: Stage = newStage()
    override val DISPATCH: Stage = newStage()
    plugins ++= List(
      new DecoderArray(config, io.fetchBufferPops),
      new RenameByPRF(config)
    ).filter(_ != null)
  }
  override val ID: Stage = pDecode.ID
  override val RENAME: Stage = pDecode.RENAME
  override val DISPATCH: Stage = pDecode.DISPATCH
  addPipeline(pDecode)
  plugins ++= List(
    new DefaultStatusProvider(),
    new PhysRegFile(config.regFile),
    new ReorderBuffer(config)
  ).filter(_ != null)
}

object DecodeSimRTL {
  def main(args: Array[String]) {
    val spinalConfig = new SpinalConfig {
      override val defaultConfigForClockDomains: ClockDomainConfig =
        ClockDomainConfig(resetKind = SYNC)
      override val targetDirectory: String = "generated_verilog/ZenCove/"
      override val headerWithDate: Boolean = true
    }
    val loongConfig = ZencoveConfig()
    val dir = Directory(spinalConfig.targetDirectory)
    if (!dir.exists) dir.jfile.mkdirs()

    spinalConfig.generateVerilog(new DecodeSimRTL(loongConfig)).printUnused()
  }
}
