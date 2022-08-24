package zencove.core

import spinal.core._
import spinal.lib._
import zencove.builder._
import zencove.enum._
import zencove.util._
import zencove.model._
import zencove.MIPS32._

class ExceptionHandler extends Plugin[CPU] with StatusProvider {
  override val statusBEV = RegInit(True)
  override val statusERL = RegInit(True)
  override val statusEXL = RegInit(False)
  val statusUM = RegInit(False) // 监控程序背锅，它的user也跑在kernel mode
  val baseMode = Mux(statusUM, PrivMode.USER, PrivMode.KERNEL)
  override val statusCU0 = Reg(Bool)
  //BEV 22, 1 on reset, bootstrap vector
  //IM4 12, interrupt mask 4(timer)
  //ERL 2, Error Level(In kernel mode)
  //EXL 1, Exception level
  //IE 0, interrupt enable

  val causeBD = RegInit(False)
  val causeExcCode = RegInit(B(0, 5 bits))
  //BD 31, exception in delay slot
  //IV 23, interrupt use special vector
  //IP4 12, interrupt pending(timer)
  //ExcCode 6~2, exception code

  val epc = RegInit(UWord(0)) //14
  val errorEpc = RegInit(UWord(0))
  val ebaseBase = RegInit(U(0x80000, 20 bits))
  //Exception Base 29~12
  //Note that 31 must be 1 and 30, 11~10 must be 0

  //Context Register 4,0
  val PTEBase = RegInit(U(0, 9 bits)) //Page table entry base, rw, not modifiable
  val BadVPN2 = RegInit(U(0, 19 bits)) //Bad vpn 31~13, r
  //Context 3~0 is hardwired 0

  //BadVaddr 8,0
  val BadVaddr = RegInit(UWord(0)) //bad virtual address, r

  // operating (privilege) mode, default in kernel
  override val privMode = PrivMode.KERNEL()

  override def setup(pipeline: CPU): Unit = {
    val cp0 = pipeline.service[CP0Regs]
    val programCounter = pipeline.pFetch.service[ProgramCounter]

    cp0.rw(CP0.Context, 23 -> PTEBase)
    cp0.r(CP0.Context, 4 -> BadVPN2)

    cp0.r(CP0.BadVaddr, 0 -> BadVaddr)

    cp0.rw(CP0.EPC, 0 -> epc)
    cp0.rw(CP0.ErrorEPC, 0 -> errorEpc)

    /* CE - 29:28 - 不可用的COP号，只实现了COP0，所以永远是0 */
    cp0.r(CP0.Cause, 31 -> causeBD, 28 -> U(0, 2 bits))
    cp0.rw(CP0.Cause, 2 -> causeExcCode)

    cp0.rw(CP0.Status, 28 -> statusCU0, 22 -> statusBEV)
    cp0.rw(CP0.Status, 4 -> statusUM, 2 -> statusERL, 1 -> statusEXL)

    cp0.r(CP0.EBase, 12 -> ebaseBase)
    cp0.w(CP0.EBase, 12 -> ebaseBase(17 downto 0))
  }
  override def build(pipeline: CPU): Unit = pipeline plug new Area {
    import zencove.MIPS32.ExceptionCode

    val jumpInterface = pipeline.pFetch.service[ProgramCounter].backendJumpInterface
    val intVecOffset = pipeline.service[IntStatusProvider].intVecOffset
    val excCommit = pipeline.service[ExceptionCommit]
    val except = excCommit.except
    val excCode = except.payload.code
    // handle operating mode
    when(normalLevel)(privMode := baseMode)

    // 选择异常向量地址
    val base = UWord()
    when(excCode === ExceptionCode.cacheError) {
      statusERL := True
      base := statusBEV.mux(
        False -> (U(5, 3 bits) @@ ebaseBase(16 downto 0) @@ U(0, 12 bits)),
        True -> UWord(0xbfc00200L)
      )
    } otherwise {
      base := statusBEV.mux(
        False -> ebaseBase @@ U(0, 12 bits),
        True -> UWord(0xbfc00200L)
      )
    }
    // default 0x180
    val offset = U(0x180, 12 bits)
    when(
      !statusEXL && excCode.isAnyOf(
        ExceptionCode.tlbLoadOrIF,
        ExceptionCode.tlbStore
      ) && except.isTLBRefill
    ) {
      // TLB refill & EXL=0
      offset := 0
    } elsewhen (excCode === ExceptionCode.cacheError) {
      offset := 0x100
    }
    // interrupt永远优先
    val takeInt = if (intVecOffset != null) {
      when(intVecOffset.valid)(offset := intVecOffset.payload)
      intVecOffset.valid
    } else {
      False
    }

    when(except.valid) {
      jumpInterface.valid := True
      jumpInterface.payload := base + offset
      when(!statusEXL) {
        when(excCommit.exceptInDelaySlot) {
          epc := (excCommit.epc - 4)
          causeBD := True
        } otherwise {
          epc := excCommit.epc
          causeBD := False
        }
        // 中断优先，覆盖掉异常
        causeExcCode := Mux(
          takeInt,
          B(ExceptionCode.interrupt).resized,
          excCode
        )
      }
      statusEXL := True
      // 注意如果处理的是中断就不要写入异常该写的寄存器了
      when(
        !takeInt && excCode.isAnyOf(
          ExceptionCode.tlbLoadOrIF,
          ExceptionCode.tlbModification,
          ExceptionCode.tlbStore
        )
      ) {
        BadVPN2 := except.badVA(31 downto 13)
        pipeline.service[TLB].regVPN2 := except.badVA(31 downto 13)
      }
      when(
        !takeInt && excCode.isAnyOf(
          ExceptionCode.tlbLoadOrIF,
          ExceptionCode.tlbModification,
          ExceptionCode.tlbStore,
          ExceptionCode.addrErrorLoadOrIF,
          ExceptionCode.addrErrorStore
        )
      ) {
        BadVaddr := except.badVA
      }
    } elsewhen (excCommit.eret) {
      // 没有异常，处理ERET
      jumpInterface.valid := True
      jumpInterface.payload := Mux(statusERL, errorEpc, epc)
      when(statusERL) {
        statusERL := False
      } otherwise {
        statusEXL := False
      }
    }
  }
}
