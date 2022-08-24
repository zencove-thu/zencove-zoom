package zencove.blackbox.mem

import spinal.core._

class xpm_memory_sdpram_generic extends Generic {
  var ADDR_WIDTH_A = 6
  var ADDR_WIDTH_B = 6
  var AUTO_SLEEP_TIME = 0
  var BYTE_WRITE_WIDTH_A = 32
  var CASCADE_HEIGHT = 0
  var CLOCKING_MODE = "common_clock"
  var ECC_MODE = "no_ecc"
  var MEMORY_INIT_FILE = "none"
  var MEMORY_INIT_PARAM = "0"
  var MEMORY_OPTIMIZATION = "true"
  var MEMORY_PRIMITIVE = "auto"
  var MEMORY_SIZE = 2048
  var MESSAGE_CONTROL = 0
  var READ_DATA_WIDTH_B = 32
  var READ_LATENCY_B = 2
  var READ_RESET_VALUE_B = "0"
  var RST_MODE_A = "SYNC"
  var RST_MODE_B = "SYNC"
  var SIM_ASSERT_CHK = 0
  var USE_EMBEDDED_CONSTRAINT = 0
  var USE_MEM_INIT = 0
  var WAKEUP_TIME = "disable_sleep"
  var WRITE_DATA_WIDTH_A = 32
  var WRITE_MODE_B = "no_change"
}

class xpm_memory_sdpram(param: xpm_memory_sdpram_generic) extends BlackBox {
  val generic = param

  val io = new Bundle {
    val read = new Bundle {
      val clk = in(Bool)
      val rst = in(Bool)
      val en = in(Bool)
      val regce = in(Bool)
      val addr = in(UInt(generic.ADDR_WIDTH_B bits))
      val dout = out(Bits(generic.READ_DATA_WIDTH_B bits))
      val sbiterr = out(Bool)
      val dbiterr = out(Bool)
    }
    val write = new Bundle {
      val clk = in(Bool)
      val en = in(Bool)
      val we = in(Bits(generic.WRITE_DATA_WIDTH_A / generic.BYTE_WRITE_WIDTH_A bits))
      val addr = in(UInt(generic.ADDR_WIDTH_A bits))
      val din = in(Bits(generic.WRITE_DATA_WIDTH_A bits))
      val injectsbiterr = in(Bool) default False
      val injectdbiterr = in(Bool) default False
    }
    val sleep = in(Bool) default False
  }

  addPrePopTask(() => {
    io.flatten.foreach(bt => {
      if (bt.getName().contains("write"))
        bt.setName(bt.getName().replace("write_", "") + "a")
      if (bt.getName().contains("read"))
        bt.setName(bt.getName().replace("read_", "") + "b")
    })
  })

  noIoPrefix
  mapClockDomain(clock = io.write.clk)
  mapClockDomain(clock = io.read.clk, reset = io.read.rst, resetActiveLevel = HIGH)
}

class xpm_memory_tdpram_generic extends Generic {
  var ADDR_WIDTH_A = 6 // DECIMAL
  var ADDR_WIDTH_B = 6 // DECIMAL
  var AUTO_SLEEP_TIME = 0 // DECIMAL
  var BYTE_WRITE_WIDTH_A = 32 // DECIMAL
  var BYTE_WRITE_WIDTH_B = 32 // DECIMAL
  var CASCADE_HEIGHT = 0 // DECIMAL
  var CLOCKING_MODE = "common_clock" // String
  var ECC_MODE = "no_ecc" // String
  var MEMORY_INIT_FILE = "none" // String
  var MEMORY_INIT_PARAM = "0" // String
  var MEMORY_OPTIMIZATION = "true" // String
  var MEMORY_PRIMITIVE = "block" // String
  var MEMORY_SIZE = 2048 // DECIMAL
  var MESSAGE_CONTROL = 0 // DECIMAL
  var READ_DATA_WIDTH_A = 32 // DECIMAL
  var READ_DATA_WIDTH_B = 32 // DECIMAL
  var READ_LATENCY_A = 1 // DECIMAL
  var READ_LATENCY_B = 1 // DECIMAL
  var READ_RESET_VALUE_A = "0" // String
  var READ_RESET_VALUE_B = "0" // String
  var RST_MODE_A = "SYNC" // String
  var RST_MODE_B = "SYNC" // String
  var SIM_ASSERT_CHK = 0 // DECIMAL; 0=disable simulation messages 1=enable simulation messages
  var USE_EMBEDDED_CONSTRAINT = 0 // DECIMAL
  var USE_MEM_INIT = 0 // DECIMAL
  var WAKEUP_TIME = "disable_sleep" // String
  var WRITE_DATA_WIDTH_A = 32 // DECIMAL
  var WRITE_DATA_WIDTH_B = 32 // DECIMAL
  var WRITE_MODE_A = "no_change" // String
  var WRITE_MODE_B = "no_change" // String
}

class xpm_memory_tdpram(param: xpm_memory_tdpram_generic) extends BlackBox {
  val generic = param

  val io = new Bundle {
    val porta = XPMMemPort(
      generic.WRITE_DATA_WIDTH_A,
      generic.READ_DATA_WIDTH_A,
      generic.ADDR_WIDTH_A,
      generic.WRITE_DATA_WIDTH_A / generic.BYTE_WRITE_WIDTH_A
    )
    val portb = XPMMemPort(
      generic.WRITE_DATA_WIDTH_B,
      generic.READ_DATA_WIDTH_B,
      generic.ADDR_WIDTH_B,
      generic.WRITE_DATA_WIDTH_B / generic.BYTE_WRITE_WIDTH_B
    )
    val sleep = in(Bool) default False
  }

  addPrePopTask(() => {
    io.flatten.foreach(bt => {
      if (bt.getName().contains("porta"))
        bt.setName(bt.getName().replace("porta_", "") + "a")
      if (bt.getName().contains("portb"))
        bt.setName(bt.getName().replace("portb_", "") + "b")
    })
  })
  noIoPrefix
  mapClockDomain(clock = io.porta.clk, reset = io.porta.rst, resetActiveLevel = HIGH)
  mapClockDomain(clock = io.portb.clk, reset = io.portb.rst, resetActiveLevel = HIGH)
}

class xpm_memory_dpdistram_generic extends Generic {
  var ADDR_WIDTH_A = 6 // DECIMAL
  var ADDR_WIDTH_B = 6 // DECIMAL
  var BYTE_WRITE_WIDTH_A = 32 // DECIMAL
  var CLOCKING_MODE = "common_clock" // String
  var MEMORY_INIT_FILE = "none" // String
  var MEMORY_INIT_PARAM = "0" // String
  var MEMORY_OPTIMIZATION = "true" // String
  var MEMORY_SIZE = 2048 // DECIMAL
  var MESSAGE_CONTROL = 0 // DECIMAL
  var READ_DATA_WIDTH_A = 32 // DECIMAL
  var READ_DATA_WIDTH_B = 32 // DECIMAL
  var READ_LATENCY_A = 1 // DECIMAL
  var READ_LATENCY_B = 1 // DECIMAL
  var READ_RESET_VALUE_A = "0" // String
  var READ_RESET_VALUE_B = "0" // String
  var RST_MODE_A = "SYNC" // String
  var RST_MODE_B = "SYNC" // String
  var SIM_ASSERT_CHK = 0 // DECIMAL; 0=disable simulation messages, 1=enable simulation messages
  var USE_EMBEDDED_CONSTRAINT = 0 // DECIMAL
  var USE_MEM_INIT = 0 // DECIMAL
  var WRITE_DATA_WIDTH_A = 32 // DECIMAL
}

class xpm_memory_dpdistram(param: xpm_memory_dpdistram_generic) extends BlackBox {
  val generic = param

  val io = new Bundle {
    val rw = new Bundle {
      val clk = in(Bool)
      val rst = in(Bool)
      val en = in(Bool)
      val we = in(Bits(generic.WRITE_DATA_WIDTH_A / generic.BYTE_WRITE_WIDTH_A bits))
      val regce = in(Bool)
      val addr = in(UInt(generic.ADDR_WIDTH_A bits))
      val dout = out(Bits(generic.READ_DATA_WIDTH_A bits))
      val din = in(Bits(generic.WRITE_DATA_WIDTH_A bits))
    }
    val read = new Bundle {
      val clk = in(Bool)
      val rst = in(Bool)
      val en = in(Bool)
      val regce = in(Bool)
      val addr = in(UInt(generic.ADDR_WIDTH_B bits))
      val dout = out(Bits(generic.READ_DATA_WIDTH_B bits))
    }
  }
  noIoPrefix()
  addPrePopTask(() => {
    io.flatten.foreach(bt => {
      if (bt.getName().startsWith("rw"))
        bt.setName(bt.getName().replace("rw_", "") + "a")
      if (bt.getName().startsWith("read"))
        bt.setName(bt.getName().replace("read_", "") + "b")
    })
  })
  mapClockDomain(clock = io.rw.clk, reset = io.rw.rst, resetActiveLevel = HIGH)
  mapClockDomain(clock = io.read.clk, reset = io.read.rst, resetActiveLevel = HIGH)
}

class xpm_memory_spram_generic extends Generic {
  var ADDR_WIDTH_A = 6 // DECIMAL
  var AUTO_SLEEP_TIME = 0 // DECIMAL
  var BYTE_WRITE_WIDTH_A = 32 // DECIMAL
  var CASCADE_HEIGHT = 0 // DECIMAL
  var ECC_MODE = "no_ecc" // String
  var MEMORY_INIT_FILE = "none" // String
  var MEMORY_INIT_PARAM = "0" // String
  var MEMORY_OPTIMIZATION = "true" // String
  var MEMORY_PRIMITIVE = "block" // String
  var MEMORY_SIZE = 2048 // DECIMAL
  var MESSAGE_CONTROL = 0 // DECIMAL
  var READ_DATA_WIDTH_A = 32 // DECIMAL
  var READ_LATENCY_A = 1 // DECIMAL
  var READ_RESET_VALUE_A = "0" // String
  var RST_MODE_A = "SYNC" // String
  var SIM_ASSERT_CHK = 0 // DECIMAL; 0=disable simulation messages 1=enable simulation messages
  var USE_MEM_INIT = 0 // DECIMAL
  var WAKEUP_TIME = "disable_sleep" // String
  var WRITE_DATA_WIDTH_A = 32 // DECIMAL
  var WRITE_MODE_A = "no_change" // String
}

class xpm_memory_spram(param: xpm_memory_tdpram_generic) extends BlackBox {
  val generic = param

  val io = new Bundle {
    val porta = XPMMemPort(
      generic.WRITE_DATA_WIDTH_A,
      generic.READ_DATA_WIDTH_A,
      generic.ADDR_WIDTH_A,
      generic.WRITE_DATA_WIDTH_A / generic.BYTE_WRITE_WIDTH_A
    )
    val sleep = in(Bool) default False
  }

  addPrePopTask(() => {
    io.flatten.foreach(bt => {
      if (bt.getName().contains("porta"))
        bt.setName(bt.getName().replace("porta_", "") + "a")
    })
  })
  noIoPrefix
  mapClockDomain(clock = io.porta.clk, reset = io.porta.rst, resetActiveLevel = HIGH)
}
