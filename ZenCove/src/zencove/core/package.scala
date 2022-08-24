package zencove

import zencove.builder._
import spinal.core._
import spinal.lib._
import zencove.util._
import zencove.model._
import zencove.enum._

package object core {
  object ADDRESS_CACHED extends Stageable(Bool)
  object BAD_VADDR extends Stageable(UWord())
  object EXCEPTION_CODE extends Stageable(Bits(5 bits))
  object EXCEPTION_OCCURRED extends Stageable(Bool)
  object IS_LOAD extends Stageable(Bool)
  object IS_STORE extends Stageable(Bool)
  object IS_TLB_REFILL extends Stageable(Bool)
  object LOAD_STORE_TYPE extends Stageable(LoadStoreType())
  object PC extends Stageable(UWord())
  object PC_PHYSICAL extends Stageable(UWord())
  object PREDICT_ADDR extends Stageable(UWord())

  /** memory信号组合：(from issue) retired | cached | (from uop) is store | is load |
    *
    * load address: 0x 01 (若cached，同时load data)
    *
    * cache operation: 0x 00
    *
    * store address: 0x 10 (若cached，同时填入store buffer)
    *
    * 以上cached信号值会在MEM1由AGU确定
    *
    * load data (uncached): 10 01
    *
    * store data (cached): 11 xx (只有这种情况uop无效)
    *
    * store data (uncached): 10 10
    */
  object MEMORY_ADDRESS extends Stageable(UWord())
  object MEMORY_ADDRESS_PHYSICAL extends Stageable(UWord())
  object MEMORY_BE extends Stageable(Bits(4 bits))
  object MEMORY_RETIRED extends Stageable(Bool)
  object MEMORY_READ_DATA extends Stageable(BWord())
  object MEMORY_WRITE_DATA extends Stageable(BWord())
}
