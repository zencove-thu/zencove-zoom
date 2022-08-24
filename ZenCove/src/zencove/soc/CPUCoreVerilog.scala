package zencove.soc

import scala.reflect.io.Directory
import spinal.core._
import spinal.lib.bus.amba4.axi._
import spinal.lib._
import zencove.util._
import zencove.blackbox._
import zencove.ZencoveConfig
import zencove.model.DebugInterface
import scopt.OParser
import zencove.core.CPUCore
import zencove.ISAConfig
import zencove.InterruptConfig

object CPUCoreVerilog {
  def main(args: Array[String]) {
    val parser = new scopt.OptionParser[CLIConfig]("CPUTopVerilog") {
      opt[Unit]("sim")
        .action { (_, c) => c.copy(inSim = true) }
        .text("enable debug ports used in simulation")
      opt[String]("scale")
        .action { (x, c) => c.copy(scale = x) }
        .text("set scale to normal/tiny")
      opt[Unit]("no-timer")
        .action { (_, c) => c.copy(timer = false) }
        .text("disable timer interrupt")
    }

    val cliConfig = parser.parse(args, CLIConfig()) match {
      case Some(value) => value
      case None        => return
    }
    if (cliConfig.inSim) println("Info: compiling for simulation")
    printf("Info: scale is %s\n", cliConfig.scale)

    val spinalConfig = SpinalConfig(
      removePruned = !cliConfig.inSim,
      targetDirectory = "generated_verilog",
      headerWithDate = true,
      defaultConfigForClockDomains = ClockDomainConfig(resetActiveLevel = LOW)
    )
    val dir = Directory(spinalConfig.targetDirectory)
    if (!dir.exists) dir.jfile.mkdirs()

    val axiConfig = Axi4Config(
      addressWidth = 32,
      dataWidth = 32,
      idWidth = 4,
      useRegion = false,
      useQos = false
    )

    val interruptConfig = InterruptConfig(
      withTimerInt = cliConfig.timer
    )

    if (cliConfig.timer)
      printf("Info: Timer interrupt enabled\n")
    else
      printf("Info: Timer interrupt disabled\n")

    val cpuConfig = (cliConfig.scale match {
      case "tiny"    => ZencoveConfig.tiny
      case "smaller" => ZencoveConfig.smaller
      case "small"   => ZencoveConfig.small
      case "normal"  => ZencoveConfig.normal
      case "wide"    => ZencoveConfig.wide
      case _         => ZencoveConfig()
    }).copy(
      axi = axiConfig,
      debug = cliConfig.inSim,
      isa = ISAConfig.mips32,
      interrupt = interruptConfig
    )

    spinalConfig.generateVerilog(new CPUCore(cpuConfig))
    //.printUnused()
    //.printPruned()
    //.printPrunedIo()
  }
}
