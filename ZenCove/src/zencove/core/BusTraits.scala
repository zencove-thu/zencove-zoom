package zencove.core

import spinal.lib.bus.amba4.axi._

trait IBus {
  val iBus: Axi4ReadOnly
}

trait DBus {
  val dBus: Axi4
}

trait UDBus {
  val udBus: Axi4
}
