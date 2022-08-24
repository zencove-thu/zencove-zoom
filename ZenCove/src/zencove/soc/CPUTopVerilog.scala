package zencove.soc

import scala.reflect.io.Directory
import spinal.core._
import scopt.OParser

object CPUTopVerilog {
  def main(args: Array[String]) {
    val parser = new scopt.OptionParser[CLIConfig]("CPUTopVerilog") {
      opt[Unit]("sim")
        .action { (_, c) => c.copy(inSim = true) }
        .text("enable debug ports used in simulation")
      opt[String]("scale")
        .action { (x, c) => c.copy(scale = x) }
        .text("set scale to normal/tiny")
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
      headerWithDate = true
    )
    val dir = Directory(spinalConfig.targetDirectory)
    if (!dir.exists) dir.jfile.mkdirs()

    spinalConfig.generateVerilog(new CPUTop(debug = cliConfig.inSim, scale = cliConfig.scale))
    //.printUnused()
    //.printPruned()
    //.printPrunedIo()
  }
}
