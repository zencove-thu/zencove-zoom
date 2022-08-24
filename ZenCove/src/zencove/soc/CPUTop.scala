package zencove.soc

import spinal.core._
import spinal.lib.bus.amba4.axi._
import spinal.lib._
import zencove.util._
import zencove.blackbox._
import zencove._
import zencove.model.DebugInterface
import zencove.misc._

class CPUTop(debug: Boolean = false, scale: String = "normal") extends Component {
  setDefinitionName("mycpu_top")
  noIoPrefix()
  // AXI4 config of loongson soc
  val axiConfig = Axi4Config(
    addressWidth = 32,
    dataWidth = 32,
    idWidth = 4,
    useRegion = false,
    useQos = false
  )
  val cpuConfig = (scale match {
    case "tiny"    => ZencoveConfig.tiny
    case "smaller" => ZencoveConfig.smaller
    case "small"   => ZencoveConfig.small
    case "normal"  => ZencoveConfig.normal
    case "wide"    => ZencoveConfig.wide
    case _         => ZencoveConfig()
  }).copy(axi = axiConfig, debug = debug, isa = ISAConfig.mips1)
  val io = new Bundle {
    val aclk = in(Bool)
    val aresetn = in(Bool)

    val extInt = in(Bits(6 bits)).setName("ext_int")
    // cpu bus
    val axi = master(Axi4(axiConfig)).setName("")
    // unexpected AXI signal
    val wid = out(UInt(axiConfig.idWidth bits))
    val debug = out(DebugInterface())
  }
  val defaultClockDomain = ClockDomain(
    clock = io.aclk,
    reset = io.aresetn,
    config = ClockDomainConfig(resetActiveLevel = LOW)
  )
  val defaultClockArea = new ClockingArea(defaultClockDomain) {
    val cpu = new zencove.core.CPUCore(cpuConfig)
    cpu.io.extInt := io.extInt
    if (cpuConfig.debug) io.debug := cpu.io.debug
    else io.debug.assignDontCare()
    val crossbar = new zencove.peripheral.AxiCrossbar(axiConfig)
    cpu.io.iBus >> crossbar.io.iBus
    cpu.io.dBus >> crossbar.io.dBus

    if (cpuConfig.useUdBusBuffer) {
      val axiBuffer = new AxiBuffer(axiConfig, cpuConfig.dcache.waitForB)
      cpu.io.udBus <> axiBuffer.io.in_axi
      axiBuffer.io.out_axi >> crossbar.io.udBus
    } else {
      cpu.io.udBus >> crossbar.io.udBus
    }

    crossbar.io.cpuBus >> io.axi
    // val crossbar = CrossBarIP(3, 1)
    // CrossBarIP.connect(crossbar, Array(cpu.io.iBus, cpu.io.dBus, cpu.io.udBus), Array(io.axi))
    // 每次发出写请求时就寄存一次wid
    io.wid := RegNextWhen(io.axi.aw.id, io.axi.aw.valid, U(0))
    when(io.axi.aw.valid) { io.wid := io.axi.aw.id }
  }.setCompositeName(this)
  addPrePopTask { () =>
    prunePayloadRecursive(io)
    Axi4Naming.setAllAXIName(io)
  }
}
