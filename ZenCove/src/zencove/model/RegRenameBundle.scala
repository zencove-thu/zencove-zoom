package zencove.model

import spinal.core._
import spinal.lib._

final case class RegRenameBundle(arfAddrWidth: Int, prfAddrWidth: Int)
    extends Bundle
    with IMasterSlave {
  val req = Flow(UInt(arfAddrWidth bits))
  val rsp = UInt(prfAddrWidth bits)
  override def asMaster(): Unit = {
    master(req)
    in(rsp)
  }
}
