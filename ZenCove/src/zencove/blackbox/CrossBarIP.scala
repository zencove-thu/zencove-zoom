package zencove.blackbox

import spinal.core._
import spinal.lib.bus.amba4.axi._

/** m slave x n master CrossBar IP BlackBox */
case class CrossBarIP(m: Int, n: Int) extends BlackBox {
  setDefinitionName("axi_crossbar_%dx%d".format(m, n))

  val io = new Bundle {
    val aclk = in Bool ()
    val aresetn = in Bool ()
    //s_axi和Master连接
    val s_axi = new Bundle {
      val aw = new Bundle {
        val id = in Bits (4 * m bits)
        val addr = in Bits (32 * m bits)
        val len = in Bits (8 * m bits)
        val size = in Bits (3 * m bits)
        val burst = in Bits (2 * m bits)
        val lock = in Bits (m bits)
        val cache = in Bits (4 * m bits)
        val prot = in Bits (3 * m bits)
        val qos = in Bits (4 * m bits)
        val valid = in Bits (m bits)
        val ready = out Bits (m bits)
      }
      val w = new Bundle {
        val data = in Bits (32 * m bits)
        val strb = in Bits (4 * m bits)
        val last = in Bits (m bits)
        val valid = in Bits (m bits)
        val ready = out Bits (m bits)
      }
      val b = new Bundle {
        val id = out Bits (4 * m bits)
        val resp = out Bits (2 * m bits)
        val valid = out Bits (m bits)
        val ready = in Bits (m bits)
      }
      val ar = new Bundle {
        val id = in Bits (4 * m bits)
        val addr = in Bits (32 * m bits)
        val len = in Bits (8 * m bits)
        val size = in Bits (3 * m bits)
        val burst = in Bits (2 * m bits)
        val lock = in Bits (m bits)
        val cache = in Bits (4 * m bits)
        val prot = in Bits (3 * m bits)
        val qos = in Bits (4 * m bits)
        val valid = in Bits (m bits)
        val ready = out Bits (m bits)
      }
      val r = new Bundle {
        val id = out Bits (4 * m bits)
        val data = out Bits (32 * m bits)
        val resp = out Bits (2 * m bits)
        val last = out Bits (m bits)
        val valid = out Bits (m bits)
        val ready = in Bits (m bits)
      }
    }
    //m_axi和Slave连接
    val m_axi = new Bundle {
      val aw = new Bundle {
        val id = out Bits (4 * n bits)
        val addr = out Bits (32 * n bits)
        val len = out Bits (8 * n bits)
        val size = out Bits (3 * n bits)
        val burst = out Bits (2 * n bits)
        val lock = out Bits (n bits)
        val cache = out Bits (4 * n bits)
        val prot = out Bits (3 * n bits)
        val region = out Bits (4 * n bits)
        val qos = out Bits (4 * n bits)
        val valid = out Bits (n bits)
        val ready = in Bits (n bits)
      }
      val w = new Bundle {
        val data = out Bits (32 * n bits)
        val strb = out Bits (4 * n bits)
        val last = out Bits (n bits)
        val valid = out Bits (n bits)
        val ready = in Bits (n bits)
      }
      val b = new Bundle {
        val id = in Bits (4 * n bits)
        val resp = in Bits (2 * n bits)
        val valid = in Bits (n bits)
        val ready = out Bits (n bits)
      }
      val ar = new Bundle {
        val id = out Bits (4 * n bits)
        val addr = out Bits (32 * n bits)
        val len = out Bits (8 * n bits)
        val size = out Bits (3 * n bits)
        val burst = out Bits (2 * n bits)
        val lock = out Bits (n bits)
        val cache = out Bits (4 * n bits)
        val prot = out Bits (3 * n bits)
        val region = out Bits (4 * n bits)
        val qos = out Bits (4 * n bits)
        val valid = out Bits (n bits)
        val ready = in Bits (n bits)
      }
      val r = new Bundle {
        val id = in Bits (4 * n bits)
        val data = in Bits (32 * n bits)
        val resp = in Bits (2 * n bits)
        val last = in Bits (n bits)
        val valid = in Bits (n bits)
        val ready = out Bits (n bits)
      }
    }
  }

  noIoPrefix()
  mapClockDomain(clock = io.aclk, reset = io.aresetn, resetActiveLevel = LOW)
  addPrePopTask(() => {
    io.flatten.foreach(bt => {
      val idx = bt.getName().lastIndexOf('_')
      if (idx >= 0) {
        bt.setName(bt.getName().substring(0, idx).concat(bt.getName().substring(idx + 1)))
      }
    })
  })
}

object CrossBarIP {
  def connect(crossbar: CrossBarIP, masters: Array[Axi4], slaves: Array[Axi4]): Unit = {
    assert(crossbar.m == masters.length && crossbar.n == slaves.length)
    //master
    crossbar.io.s_axi.aw.id := masters
      .map(ele => ele.aw.id.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.addr := masters
      .map(ele => ele.aw.addr.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.len := masters
      .map(ele => ele.aw.len.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.size := masters
      .map(ele => ele.aw.size.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.burst := masters
      .map(ele => ele.aw.burst)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.lock := masters
      .map(ele => ele.aw.lock)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.cache := masters
      .map(ele => ele.aw.cache)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.prot := masters
      .map(ele => ele.aw.prot)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.aw.qos := 0 // no QoS scheme
    crossbar.io.s_axi.aw.valid := masters
      .map(ele => ele.aw.valid.asBits)
      .reduceLeft((res, ele) => ele ## res)

    crossbar.io.s_axi.w.data := masters.map(ele => ele.w.data).reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.w.strb := masters.map(ele => ele.w.strb).reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.w.last := masters
      .map(ele => ele.w.last.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.w.valid := masters
      .map(ele => ele.w.valid.asBits)
      .reduceLeft((res, ele) => ele ## res)

    crossbar.io.s_axi.b.ready := masters
      .map(ele => ele.b.ready.asBits)
      .reduceLeft((res, ele) => ele ## res)

    crossbar.io.s_axi.ar.id := masters
      .map(ele => ele.ar.id.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.addr := masters
      .map(ele => ele.ar.addr.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.len := masters
      .map(ele => ele.ar.len.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.size := masters
      .map(ele => ele.ar.size.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.burst := masters
      .map(ele => ele.ar.burst)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.lock := masters
      .map(ele => ele.ar.lock)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.cache := masters
      .map(ele => ele.ar.cache)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.prot := masters
      .map(ele => ele.ar.prot)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.s_axi.ar.qos := 0 // no QoS scheme
    crossbar.io.s_axi.ar.valid := masters
      .map(ele => ele.ar.valid.asBits)
      .reduceLeft((res, ele) => ele ## res)

    crossbar.io.s_axi.r.ready := masters
      .map(ele => ele.r.ready.asBits)
      .reduceLeft((res, ele) => ele ## res)

    masters.zipWithIndex.foreach(ele => {
      ele._1.aw.ready := crossbar.io.s_axi.aw.ready(ele._2)
      ele._1.w.ready := crossbar.io.s_axi.w.ready(ele._2)
      ele._1.b.id := crossbar.io.s_axi.b.id(ele._2 * 4, 4 bits).asUInt
      ele._1.b.resp := crossbar.io.s_axi.b.resp(ele._2 * 2, 2 bits)
      ele._1.b.valid := crossbar.io.s_axi.b.valid(ele._2)
      ele._1.ar.ready := crossbar.io.s_axi.ar.ready(ele._2)
      ele._1.r.id := crossbar.io.s_axi.r.id(ele._2 * 4, 4 bits).asUInt
      ele._1.r.data := crossbar.io.s_axi.r.data(ele._2 * 32, 32 bits)
      ele._1.r.resp := crossbar.io.s_axi.r.resp(ele._2 * 2, 2 bits)
      ele._1.r.last := crossbar.io.s_axi.r.last(ele._2)
      ele._1.r.valid := crossbar.io.s_axi.r.valid(ele._2)
    })

    //slave
    slaves.zipWithIndex.foreach(ele => {
      ele._1.aw.id := crossbar.io.m_axi.aw.id(4 * ele._2, 4 bits).asUInt
      ele._1.aw.addr := crossbar.io.m_axi.aw.addr(32 * ele._2, 32 bits).asUInt
      ele._1.aw.len := crossbar.io.m_axi.aw.len(8 * ele._2, 8 bits).asUInt
      ele._1.aw.size := crossbar.io.m_axi.aw.size(3 * ele._2, 3 bits).asUInt
      ele._1.aw.burst := crossbar.io.m_axi.aw.burst(2 * ele._2, 2 bits)
      ele._1.aw.lock := crossbar.io.m_axi.aw.lock(ele._2).asBits
      ele._1.aw.cache := crossbar.io.m_axi.aw.cache(4 * ele._2, 4 bits)
      ele._1.aw.prot := crossbar.io.m_axi.aw.prot(3 * ele._2, 3 bits)
      ele._1.aw.valid := crossbar.io.m_axi.aw.valid(ele._2)

      ele._1.w.data := crossbar.io.m_axi.w.data(32 * ele._2, 32 bits)
      ele._1.w.strb := crossbar.io.m_axi.w.strb(4 * ele._2, 4 bits)
      ele._1.w.last := crossbar.io.m_axi.w.last(ele._2)
      ele._1.w.valid := crossbar.io.m_axi.w.valid(ele._2)

      ele._1.b.ready := crossbar.io.m_axi.b.ready(ele._2)

      ele._1.ar.id := crossbar.io.m_axi.ar.id(4 * ele._2, 4 bits).asUInt
      ele._1.ar.addr := crossbar.io.m_axi.ar.addr(32 * ele._2, 32 bits).asUInt
      ele._1.ar.len := crossbar.io.m_axi.ar.len(8 * ele._2, 8 bits).asUInt
      ele._1.ar.size := crossbar.io.m_axi.ar.size(3 * ele._2, 3 bits).asUInt
      ele._1.ar.burst := crossbar.io.m_axi.ar.burst(2 * ele._2, 2 bits)
      ele._1.ar.lock := crossbar.io.m_axi.ar.lock(ele._2).asBits
      ele._1.ar.cache := crossbar.io.m_axi.ar.cache(4 * ele._2, 4 bits)
      ele._1.ar.prot := crossbar.io.m_axi.ar.prot(3 * ele._2, 3 bits)
      ele._1.ar.valid := crossbar.io.m_axi.ar.valid(ele._2)

      ele._1.r.ready := crossbar.io.m_axi.r.ready(ele._2)
    })

    crossbar.io.m_axi.aw.ready := slaves
      .map(ele => ele.aw.ready.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.w.ready := slaves
      .map(ele => ele.w.ready.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.b.id := slaves
      .map(ele => ele.b.id.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.b.resp := slaves.map(ele => ele.b.resp).reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.b.valid := slaves
      .map(ele => ele.b.valid.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.ar.ready := slaves
      .map(ele => ele.ar.ready.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.r.id := slaves
      .map(ele => ele.r.id.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.r.data := slaves.map(ele => ele.r.data).reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.r.resp := slaves.map(ele => ele.r.resp).reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.r.last := slaves
      .map(ele => ele.r.last.asBits)
      .reduceLeft((res, ele) => ele ## res)
    crossbar.io.m_axi.r.valid := slaves
      .map(ele => ele.r.valid.asBits)
      .reduceLeft((res, ele) => ele ## res)
  }

  def main(args: Array[String]): Unit = {
    SpinalVerilog(new CrossBarIP(3, 1))
  }
}
