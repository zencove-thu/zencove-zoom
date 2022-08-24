package zencove

import spinal.core._
import spinal.lib.logic.Masked

object MIPS32 {
  // 生成指令编码。null表示don't care，空串表示0（自动长度），其它字符串必须符合对应域长度，可以包含通配'-'。
  private def makeInst(
      opcode: String = null,
      rs: String = null,
      rt: String = null,
      rd: String = null,
      sa: String = null,
      function: String = null
  ): MaskedLiteral = {
    val inst = new StringBuilder()
    def addField(field: String, length: Int) {
      inst ++= (field match {
        case null => "-".repeat(length)
        case ""   => "0".repeat(length)
        case _ => {
          assert(field.length() == length)
          field
        }
      })
    }
    addField(opcode, 6)
    addField(rs, 5)
    addField(rt, 5)
    addField(rd, 5)
    addField(sa, 5)
    addField(function, 6)
    assert(inst.length == 32)
    MaskedLiteral(inst.mkString)
  }
  private val SPECIAL = "000000"
  private val SPECIAL2 = "011100"
  private val REGIMM = "000001"
  val COP0 = "010000"
  //NOTE: Please keep the commands in the alphabetic order.
  val ADD = makeInst(opcode = SPECIAL, sa = "", function = "100000")
  val ADDI = makeInst(opcode = "001000")
  val ADDIU = makeInst(opcode = "001001")
  val ADDU = makeInst(opcode = SPECIAL, sa = "", function = "100001")
  val AND = makeInst(opcode = SPECIAL, sa = "", function = "100100")
  val ANDI = makeInst(opcode = "001100")
  val BEQ = makeInst(opcode = "000100")
  val BGEZ = makeInst(opcode = "000001", rt = "00001")
  val BGEZAL = makeInst(opcode = "000001", rt = "10001")
  val BGTZ = makeInst(opcode = "000111", rt = "")
  val BLEZ = makeInst(opcode = "000110", rt = "")
  val BLTZ = makeInst(opcode = "000001", rt = "")
  val BLTZAL = makeInst(opcode = "000001", rt = "10000")
  val BNE = makeInst(opcode = "000101")
  val BREAK = makeInst(opcode = SPECIAL, function = "001101")
  val CACHE = makeInst(opcode = "101111")
  val CLO = makeInst(opcode = SPECIAL2, sa = "", function = "100001")
  val CLZ = makeInst(opcode = SPECIAL2, sa = "", function = "100000")
  val DIV = makeInst(opcode = SPECIAL, rd = "", sa = "", function = "011010")
  val DIVU = makeInst(opcode = SPECIAL, rd = "", sa = "", function = "011011")
  val ERET =
    makeInst(opcode = "010000", rs = "10000", rt = "", rd = "", sa = "", function = "011000")
  val J = makeInst(opcode = "000010")
  val JAL = makeInst(opcode = "000011")
  val JALR = makeInst(opcode = SPECIAL, rt = "", function = "001001")
  val JR = makeInst(opcode = SPECIAL, rt = "", rd = "", function = "001000")
  val LUI = makeInst(opcode = "001111", rs = "")
  val LB = makeInst(opcode = "100000")
  val LBU = makeInst(opcode = "100100")
  val LH = makeInst(opcode = "100001")
  val LHU = makeInst(opcode = "100101")
  val LW = makeInst(opcode = "100011")
  val LWL = makeInst(opcode = "100010")
  val LWR = makeInst(opcode = "100110")
  val MADD = makeInst(opcode = SPECIAL2, rd = "", sa = "", function = "000000")
  val MADDU = makeInst(opcode = SPECIAL2, rd = "", sa = "", function = "000001")
  val MFC0 = makeInst(opcode = COP0, rs = "00000", sa = "", function = "000---")
  val MFHI = makeInst(opcode = SPECIAL, rs = "", rt = "", sa = "", function = "010000")
  val MFLO = makeInst(opcode = SPECIAL, rs = "", rt = "", sa = "", function = "010010")
  val MOVN = makeInst(opcode = SPECIAL, sa = "", function = "001011")
  val MOVZ = makeInst(opcode = SPECIAL, sa = "", function = "001010")
  val MSUB = makeInst(opcode = SPECIAL2, rd = "", sa = "", function = "000100")
  val MSUBU = makeInst(opcode = SPECIAL2, rd = "", sa = "", function = "000101")
  val MTC0 = makeInst(opcode = COP0, rs = "00100", sa = "", function = "000---")
  val MTHI = makeInst(opcode = SPECIAL, rt = "", rd = "", sa = "", function = "010001")
  val MTLO = makeInst(opcode = SPECIAL, rt = "", rd = "", sa = "", function = "010011")
  val MUL = makeInst(opcode = SPECIAL2, sa = "", function = "000010")
  val MULT = makeInst(opcode = SPECIAL, rd = "", sa = "", function = "011000")
  val MULTU = makeInst(opcode = SPECIAL, rd = "", sa = "", function = "011001")
  val NOR = makeInst(opcode = SPECIAL, sa = "", function = "100111")
  val OR = makeInst(opcode = SPECIAL, sa = "", function = "100101")
  val ORI = makeInst(opcode = "001101")
  val PREF = makeInst(opcode = "110011")
  val SB = makeInst(opcode = "101000")
  val SH = makeInst(opcode = "101001")
  val SLL = makeInst(opcode = SPECIAL, rs = "", function = "")
  val SLLV = makeInst(opcode = SPECIAL, sa = "", function = "000100")
  val SLT = makeInst(opcode = SPECIAL, sa = "", function = "101010")
  val SLTI = makeInst(opcode = "001010")
  val SLTIU = makeInst(opcode = "001011")
  val SLTU = makeInst(opcode = SPECIAL, sa = "", function = "101011")
  val SRA = makeInst(opcode = SPECIAL, rs = "", function = "000011")
  val SRAV = makeInst(opcode = SPECIAL, sa = "", function = "000111")
  val SRL = makeInst(opcode = SPECIAL, rs = "", function = "000010")
  val SRLV = makeInst(opcode = SPECIAL, sa = "", function = "000110")
  val SUB = makeInst(opcode = SPECIAL, sa = "", function = "100010")
  val SUBU = makeInst(opcode = SPECIAL, sa = "", function = "100011")
  val SW = makeInst(opcode = "101011")
  val SWL = makeInst(opcode = "101010")
  val SWR = makeInst(opcode = "101110")
  val SYNC = makeInst(opcode = SPECIAL, rs = "", rt = "", rd = "", function = "001111")
  val SYSCALL = makeInst(opcode = SPECIAL, function = "001100")
  val TEQ = makeInst(opcode = SPECIAL, function = "110100")
  val TEQI = makeInst(opcode = "000001", rt = "01100")
  val TGE = makeInst(opcode = SPECIAL, function = "110000")
  val TGEI = makeInst(opcode = "000001", rt = "01000")
  val TGEIU = makeInst(opcode = "000001", rt = "01001")
  val TGEU = makeInst(opcode = SPECIAL, function = "110001")
  val TLBP = makeInst(opcode = COP0, "10000", "", "", "", function = "001000")
  val TLBR = makeInst(opcode = COP0, "10000", "", "", "", function = "000001")
  val TLBWI = makeInst(opcode = COP0, "10000", "", "", "", function = "000010")
  val TLBWR = makeInst(opcode = COP0, "10000", "", "", "", function = "000110")
  val TLT = makeInst(opcode = SPECIAL, function = "110010")
  val TLTI = makeInst(opcode = "000001", rt = "01010")
  val TLTIU = makeInst(opcode = "000001", rt = "01011")
  val TLTU = makeInst(opcode = SPECIAL, function = "110011")
  val TNE = makeInst(opcode = SPECIAL, function = "110110")
  val TNEI = makeInst(opcode = "000001", rt = "01110")
  val WAIT = makeInst(opcode = COP0, "1----", function = "100000")
  val XOR = makeInst(opcode = SPECIAL, sa = "", function = "100110")
  val XORI = makeInst(opcode = "001110")

  object CP0 {
    private def Addr(num: Int, sel: Int) = (num << 3) | sel
    // 按地址排序（手册序）
    val Index = Addr(0, 0) //Y, TLB
    val Random = Addr(1, 0) //Y, TLB
    val EntryLo0 = Addr(2, 0) //Y, TLB
    val EntryLo1 = Addr(3, 0) //Y, TLB
    val Context = Addr(4, 0) //Y
    val PageMask = Addr(5, 0) //Y, TLB
    val Wired = Addr(6, 0) //Y, TLB
    val HWREna = Addr(7, 0) //N, TODO: Required
    val BadVaddr = Addr(8, 0) //Y, Exception Handler
    val Count = Addr(9, 0) //Y, Counter
    val EntryHi = Addr(10, 0) //Y, TLB
    val Compare = Addr(11, 0) //Y, Counter
    val Status = Addr(12, 0) //Partial Y, ExceptionHandler
    val IntCtl = Addr(12, 1) //Y, Interrupt Handler
    val SRSCtl = Addr(12, 2) //Y, Config registers
    val Cause = Addr(13, 0) //Partial Y, ExceptionHandler
    val EPC = Addr(14, 0) //Y, ExceptionHandler
    val PRId = Addr(15, 0) //Y, Config registers
    val EBase = Addr(15, 1) //Y, ExceptionHandler
    val Config = Addr(16, 0) //Y, Config registers
    val Config1 = Addr(16, 1) //Y, Config registers
    val Config2 = Addr(16, 2) //Y, Config registers
    val Config3 = Addr(16, 3) //Y, Config registers
    val ErrorEPC = Addr(30, 0) //Y, ExceptionHandler
  }

  object ExceptionCode {
    //FIXME: Not all the exceptions are implemented
    val interrupt = 0 //Y
    val tlbModification = 1 //Y
    val tlbLoadOrIF = 2 //Y
    val tlbStore = 3 //Y
    val addrErrorLoadOrIF = 4 //Y
    val addrErrorStore = 5 //Y
    val instructionBusError = 6 //N
    val dataBusError = 7 //N
    val syscall = 8 //Y
    val breakpoint = 9 //Y
    val reservedInstruction = 10 //Y
    val coprocessorUnusable = 11 //N
    val overflow = 12 //Y
    val trap = 13 //Y
    val floatingPoint = 15 //N
    val tlbReadInhibit = 19 //N
    val tlbExeInhibit = 20 //N
    val machineCheck = 24 //Y, but disabled
    val cacheError = 30 //N
  }
}
