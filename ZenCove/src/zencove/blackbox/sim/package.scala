// https://github.com/huang-jl/LLCL-MIPS/blob/main/src/main/scala/ip/sim/package.scala
package zencove.blackbox

import spinal.core._
import spinal.core.sim._
import spinal.idslplugin.PostInitCallback

import scala.collection.mutable

package object sim {

  /** 检测是否是为仿真而综合 */
  def isInSim = GenerationFlags.simulation.isEnabled

  /** 将要从外部给出的 IP 的输出拉取至顶层。 Verilator 只会在外部输入改变时重新计算模块内部各值。 如果直接驱动 IP
    * 的输出，依赖此输出的其他值不会更新，尤其是组合信号可能只会到下个周期才更新。
    */
  def pullFromOutside[T <: Data](data: T): T = {
    // 在所有 Component 之外建立该输入，并 pull 到 data 所在位置。
    val ctx = Component.push(null)
    val external = cloneOf(data).setWeakName(data.getName()).simPublic()
    // Component.pop(null)
    ctx.restore()

    data := Data.doPull(external, Component.current, useCache = true)

    // 顶层模块中得到的值为整个顶层模块的输入，仿真时应驱动此输入
    GlobalData.get.toplevel.pulledDataCache(external).asInstanceOf[T]
  }

  type Job = () => Unit

  trait JobCollector {
    val jobs = mutable.ArrayBuffer[Job]()
  }

  abstract class SimulatedBlackBox extends BlackBox with PostInitCallback {
    spinalSimWhiteBox()

    def createSimJob(): Job

    override def postInitCallback() = {
      if (GlobalData.get.toplevel.isInstanceOf[JobCollector]) {
        GlobalData.get.toplevel.asInstanceOf[JobCollector].jobs +=
          createSimJob()
      }
      super.postInitCallback()
    }
  }

  /** 模拟可能会有延迟的、自带流水线的模块。 对于延迟 > 0 的情况， 所有的读入输入操作被设计在时钟下沿，保证所有的延迟的写操作在之前完成。 对于延迟 = 0 的情况，用
    * forkSensitive 模拟组合逻辑。 此时要求 everyTick 回调在输入不变时，不写入输出；其副作用可重复执行，不产生意外影响。 （这意味着 .randomize
    * 需要受到限制）
    */
  case class Pipeline(latency: Int) {
    private val idleJob = mutable.ArrayBuffer[() => Unit]()
    private val resetJob = mutable.ArrayBuffer[() => Unit]()
    private val everyTickJobs = mutable.ArrayBuffer[((=> Unit) => Unit) => Unit]()
    private val clockDomain = ClockDomain.current

    def whenIdle(idleJob: => Unit) = {
      this.idleJob += { () => idleJob }
      this
    }

    def whenReset(resetJob: => Unit) = {
      this.resetJob += { () => resetJob }
      this
    }

    def everyTick(everyTickJob: ((=> Unit) => Unit) => Unit) = {
      everyTickJobs += everyTickJob
      this
    }

    def toJob: () => Unit =
      if (latency > 0) toJobQueued else toJobSensitive

    private def toJobQueued: () => Unit = { () =>
      var tick: Long = 0
      var resetLastCycle = false

      val pendingJobs = mutable.Queue[(() => Unit, Long)]()

      resetJob.foreach { _() }
      idleJob.foreach { _() }

      clockDomain.onActiveEdges {
        if (clockDomain.isResetAsserted) {
          if (!resetLastCycle) {
            resetJob.foreach { _() }
            resetLastCycle = true
          }
          pendingJobs.clear() // May need to be customizable
        }

        if (pendingJobs.isEmpty || pendingJobs.front._2 != tick) {
          idleJob.foreach { _() }
        } else {
          while (pendingJobs.nonEmpty && pendingJobs.front._2 == tick) {
            pendingJobs.dequeue()._1()
          }
        }
      }
      clockDomain.onFallingEdges {
        if (clockDomain.isSamplingEnable) {
          def schedule(scheduled: => Unit) = pendingJobs.enqueue((() => scheduled, tick + latency))
          everyTickJobs.foreach { _(schedule) }
        }
        tick += 1
      }
    }

    private def toJobSensitive: () => Unit = { () =>
      var resetLastDeltaCycle = false
      val pendingJobs = mutable.ArrayBuffer[() => Unit]()

      resetJob.foreach { _() }
      idleJob.foreach { _() }

      forkSensitive {
        if (!clockDomain.isResetAsserted) {
          def schedule(scheduled: => Unit) = pendingJobs += (() => scheduled)
          everyTickJobs.foreach { _(schedule) }
          if (pendingJobs.nonEmpty) {
            pendingJobs.foreach { _() }
            pendingJobs.clear()
          } else {
            idleJob.foreach { _() }
          }
        } else if (!resetLastDeltaCycle) {
          resetJob.foreach { _() }
          resetLastDeltaCycle = true
        }
      }
    }
  }
}
