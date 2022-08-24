package zencove.blackbox

import spinal.core._
import java.io._

/** ILA blackbox.
  *
  * Reference: https://aijishu.com/a/1060000000247419
  *
  * @param probePortsWidth
  *   ILA信号宽度列表。建议直接从ILA信号列表map过来。
  */
class ILA(probePortsWidth: TraversableOnce[Int]) extends BlackBox {
  setDefinitionName("ila")
  noIoPrefix()
  val io = new Bundle {
    val clk = in(Bool)
    val probe = in(Vec(probePortsWidth.map(n => Bits(n bits))))
  }
  mapClockDomain(clock = io.clk)
  afterElaboration {
    for (i <- 0 until io.probe.size) {
      io.probe(i).setName("probe" + i)
    }
    printf(
      "ILA ports cnt: %d, ports width: [%s]\n",
      probePortsWidth.size,
      probePortsWidth.mkString(", ")
    )
  }

  /** Used to generate the tcl script to configure the ILA instance.
    *
    * @param file
    *   output file
    * @param sampleDepth
    *   sample depth of ILA
    */
  def genTclScript(file: File, sampleDepth: Int = 1024) {
    val tclHeader = new PrintWriter(file)
    val getIP = s"[get_ips $definitionName]"
    tclHeader.write(s"set_property CONFIG.C_NUM_OF_PROBES ${io.probe.size} $getIP\n")
    // tclHeader.write(s"set_property CONFIG.C_EN_STRG_QUAL {1} $getIP\n")
    // tclHeader.write(s"set_property CONFIG.C_ADV_TRIGGER {true} $getIP\n")
    // tclHeader.write(s"set_property CONFIG.C_DATA_DEPTH $sampleDepth $getIP\n")
    for (i <- 0 until io.probe.size) {
      tclHeader.write(
        s"set_property CONFIG.C_PROBE${i}_WIDTH {${io.probe(i).getBitsWidth}} $getIP\n"
      )
    }
    tclHeader.close()
  }
}
