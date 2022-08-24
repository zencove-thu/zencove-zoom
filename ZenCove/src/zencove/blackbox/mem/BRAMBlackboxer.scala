package zencove.blackbox.mem

import spinal.core._
import spinal.core.internals._

class BRAMBlackboxer(policy: MemBlackboxingPolicy) extends PhaseMemBlackBoxingWithPolicy(policy) {
  def doBlackboxing(topo: MemTopology): String = {
    val mem = topo.mem
    def wrapBool(that: Expression): Bool = that match {
      case that: Bool => that
      case that =>
        val ret = Bool()
        ret.assignFrom(that)
        ret
    }

    def wrapConsumers(oldSource: Expression, newSource: Expression): Unit = {
      super.wrapConsumers(topo, oldSource, newSource)
    }

    def removeMem(): Unit = {
      super.removeMem(mem)
    }

    if (mem.initialContent != null) {
      return "Can't blackbox ROM"
    } else if (
      topo.writes.size == 1 && topo.readsSync.nonEmpty && topo.readsAsync.isEmpty && topo.writeReadSameAddressSync.isEmpty && topo.readWriteSync.isEmpty
    ) {
      if (topo.writes(0).mask != null) return "Cannot blackbox mem with write mask"
      mem.component.rework {
        val wr = topo.writes(0)

        for (rd <- topo.readsSync) {
          val ram = new BRAMGen(mem.width, log2Up(mem.wordCount))
          if (mem.width == 553 && mem.wordCount == 128) ram.setDefinitionName("icache_bram")

          ram.io.write.we := wrapBool(wr.writeEnable) && wr.clockDomain.isClockEnableActive
          ram.io.write.addr.assignFrom(wr.address)
          ram.io.write.din.assignFrom(wr.data)

          ram.io.read.en := wrapBool(rd.readEnable) && rd.clockDomain.isClockEnableActive
          ram.io.read.addr.assignFrom(rd.address)
          wrapConsumers(rd, ram.io.read.dout)

          ram.setName(mem.getName())
        }

        removeMem()
      }
    } else {
      return "Unblackboxable memory topology"
    }
    return null
  }
}
