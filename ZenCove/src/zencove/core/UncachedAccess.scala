package zencove.core

import spinal.core._
import spinal.lib._
import spinal.lib.fsm._
import spinal.lib.bus.amba4.axi._
import zencove.builder.Plugin
import zencove.ZencoveConfig
import zencove.enum._

class UncachedAccess(config: ZencoveConfig, asyncUncachedStore: Boolean = true)
    extends Plugin[MemPipeline]
    with UDBus {
  val waitForB = config.dcache.waitForB

  val udBus = Axi4(config.axi).setIdle()
  udBus.b.ready.allowOverride := True
  val uncachedStoreHandshake = Event.setIdle()
  // val uncachedWord = RegNextWhen(udBus.r.data, udBus.r.fire)

  override def build(pipeline: MemPipeline): Unit = pipeline.MEM2 plug new Area {
    import pipeline.MEM2._
    import pipeline.signals._
    val std = input(STD_SLOT)
    val isLDU = std.valid && !std.isStore
    val isSTU = std.valid && std.isStore && !std.isCached
    when(isSTU) {
      // STU直接向FSM提交写memory，要等到脏行写完
      uncachedStoreHandshake.valid := arbitration.notStuck
      arbitration.haltItself.setWhen(!uncachedStoreHandshake.ready)
    }
    val uncachedStoreFSM =
      if (asyncUncachedStore) {
        // 这个是纯异步写
        new StateMachine {
          disableAutoStart()
          setEntry(stateBoot)
          val waitAXIWriteU = new State

          val regSTD = RegNextWhen(std.payload, uncachedStoreHandshake.fire)
          uncachedStoreHandshake.setBlocked()
          val awSent, wSent = RegInit(False)
          val bRecv = RegInit(True)
          awSent setWhen (udBus.aw.fire)
          wSent setWhen (udBus.w.fire)
          bRecv setWhen (udBus.b.fire)

          stateBoot.whenIsActive {
            uncachedStoreHandshake.ready := (if (waitForB) bRecv || udBus.b.fire else True)
            when(uncachedStoreHandshake.fire) {
              awSent := False
              wSent := False
              bRecv := False
              goto(waitAXIWriteU)
            }
          }
          // uncached store
          waitAXIWriteU.whenIsActive {
            val aw = udBus.aw
            aw.valid := !awSent
            aw.payload.id := 2
            aw.payload.addr := regSTD.addr
            aw.payload.len := 0
            aw.payload.size := LoadStoreType.toAxiSize(regSTD.lsType)
            aw.payload.burst := B(1, 2 bits)
            aw.payload.lock := 0
            aw.payload.cache := 0
            if (config.axi.useQos) aw.payload.qos := 0
            aw.payload.prot := 0
            val w = udBus.w
            w.valid := !wSent
            w.data := regSTD.data
            w.strb := regSTD.be
            w.last := True
            when((awSent || aw.fire) && (wSent || w.fire)) { goto(stateBoot) }
          }
        }
      } else {
        new StateMachine {
          disableAutoStart()
          setEntry(stateBoot)
          val waitAXIWriteU = new State
          val writeMemU = new State
          val waitBU = waitForB generate new State
          val commit = new State

          // for compatibility
          val regSTD = std.payload
          // do not use handshake when in sync write
          uncachedStoreHandshake.ready := True

          stateBoot.whenIsActive {
            when(isSTU) {
              arbitration.haltItself.set()
              goto(waitAXIWriteU)
            }
          }
          // uncached store
          waitAXIWriteU.whenIsActive {
            arbitration.haltItself.set()
            val aw = udBus.aw
            aw.valid := True
            aw.payload.id := 2
            aw.payload.addr := regSTD.addr
            aw.payload.len := 0
            aw.payload.size := LoadStoreType.toAxiSize(regSTD.lsType)
            aw.payload.burst := B(1, 2 bits)
            aw.payload.lock := 0
            aw.payload.cache := 0
            if (config.axi.useQos) aw.payload.qos := 0
            aw.payload.prot := 0
            when(aw.ready) { goto(writeMemU) }
          }
          writeMemU.whenIsActive {
            arbitration.haltItself.set()
            val w = udBus.w
            w.valid := True
            w.data := regSTD.data
            w.strb := regSTD.be
            w.last := True
            when(w.ready) {
              if (waitForB) goto(waitBU) else goto(commit)
            }
          }

          waitForB generate waitBU.whenIsActive {
            arbitration.haltItself.set()
            val b = udBus.b
            when(b.valid) {
              goto(commit)
            }
          }
          commit.whenIsActive {
            when(arbitration.notStuck) { goto(stateBoot) }
          }
        }
      }

    /* val UncachedLoadFSM = new StateMachine {
      // uncached load
      val waitAXIU = new State
      val readMemU = new State
      val finishU = new State
      disableAutoStart()
      setEntry(stateBoot)

      stateBoot.whenIsActive {
        when(isLDU) {
          arbitration.haltItself.set()
          goto(waitAXIU)
        }
      }

      // uncached load
      waitAXIU.whenIsActive {
        arbitration.haltItself.set()
        val ar = udBus.ar
        ar.payload.id := 2
        ar.payload.addr := std.addr
        ar.payload.len := 0 // burst len
        ar.payload.size := LoadStoreType.toAxiSize(std.payload.lsType)
        ar.payload.burst := 1 // burst type = INCR
        ar.payload.lock := 0 // normal access
        ar.payload.cache := 0 // device non-bufferable
        if (config.axi.useQos) ar.payload.qos := 0 // no QoS scheme
        ar.payload.prot := 0 // secure and normal(non-priviledged)
        // uncached load要等到脏行写完再进行
        // 尽量让uncached load也不要与正在进行中的uncached store重叠
        ar.valid := uncachedStoreHandshake.ready && pipeline.service[DCacheWriteBack].writebackIdle
        when(ar.fire) { goto(readMemU) }
      }

      readMemU.whenIsActive {
        arbitration.haltItself.set()
        val r = udBus.r
        r.ready.set()
        when(r.valid && r.payload.last) {
          goto(finishU)
        }
      }

      finishU.whenIsActive {
        // 每一条都从state boot开始
        when(!arbitration.isStuck) {
          goto(stateBoot)
        }
      }
    } */
  }
}
