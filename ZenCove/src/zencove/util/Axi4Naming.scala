package zencove.util

import spinal.core._
import spinal.lib._
import spinal.lib.bus.amba4.axi.Axi4

object Axi4Naming {
  def setAXIName(bus: MultiData) {
    bus.flattenForeach { signal =>
      val name = signal.getName()
      val idx = name.lastIndexOf('_')
      if (idx >= 0) signal.setName(name.substring(0, idx) + name.substring(idx + 1))
    }
  }

  def setAllAXIName(bundle: Bundle) {
    bundle.elements.foreach { case (name, ref) =>
      ref match {
        case b: Axi4 => setAXIName(b)
        case _       =>
      }
    }
  }
}
