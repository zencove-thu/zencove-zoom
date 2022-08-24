package zencove.sim

import java.io.{DataInputStream, FileInputStream}
import scala.collection.mutable.ArrayBuffer

class DataProvider {
  var baseAddr: Long = 0
  var dataset: Vector[Long] = Vector[Long]()

  def isValidAddr(addr: Long) = {
    val idx = (addr - baseAddr).toInt / 4
    0 <= idx && idx < dataset.length
  }

  def get(addr: Long): Long = {
    val idx = (addr - baseAddr).toInt / 4
    if (0 <= idx && idx < dataset.length) {
      dataset(idx)
    } else {
      0
    }
  }

  def set(addr: Long, data: Long, byteEnable: Int) {
    val idx = (addr - baseAddr).toInt / 4
    var mask: Long = 0
    if ((byteEnable & 1) != 0) mask |= 0xffL
    if ((byteEnable & 2) != 0) mask |= 0xffL << 8
    if ((byteEnable & 4) != 0) mask |= 0xffL << 16
    if ((byteEnable & 8) != 0) mask |= 0xffL << 24
    if (0 <= idx && idx < dataset.length) {
      var word = dataset(idx)
      word &= ~mask
      word |= data & mask
      dataset = dataset.updated(idx, word)
    }
  }

  def setWord(addr: Long, data: Long) {
    val idx = (addr - baseAddr).toInt / 4
    if (0 <= idx && idx < dataset.length) {
      dataset = dataset.updated(idx, data)
    }
  }

  def setByte(addr: Long, data: Long) {
    val idx = (addr - baseAddr).toInt / 4
    val offset = (addr - baseAddr).toInt - idx * 4
    var word = dataset(idx)
    word &= ~(0xffL << (offset * 8))
    word |= (data & 0xffL) << (offset * 8)
    if (0 <= idx && idx < dataset.length) {
      dataset = dataset.updated(idx, word)
    }
  }

  def setHalf(addr: Long, data: Long) {
    val idx = (addr - baseAddr).toInt / 4
    val offset = (addr - baseAddr).toInt - idx * 4
    var word = dataset(idx)
    word &= ~(0xffffL << (offset * 8))
    word |= (data & 0xffffL) << (offset * 8)
    if (0 <= idx && idx < dataset.length) {
      dataset = dataset.updated(idx, word)
    }
  }

  override def toString(): String = {
    val sb = new StringBuilder()
    for (i <- dataset) {
      sb.append(s"${i.toHexString}\n")
    }
    sb.toString()
  }
}

object DataProvider {
  def apply(baseAddr: Long, dataset: Vector[Long]): DataProvider = {
    val p = new DataProvider
    p.baseAddr = baseAddr
    p.dataset = dataset
    p
  }

  def fromBinary(filename: String, baseAddr: Long): DataProvider = {
    val f = new DataInputStream(new FileInputStream(filename))
    val a = new ArrayBuffer[Long]
    while (f.available() >= 4) {
      a.append(
        Integer.toUnsignedLong(
          f.readUnsignedByte() |
            f.readUnsignedByte() << 8 |
            f.readUnsignedByte() << 16 |
            f.readUnsignedByte() << 24
        )
      )
    }
    f.close()
    DataProvider(baseAddr, a.toVector)
  }
}
