package zencove.core

import zencove.builder._
import spinal.core._
import scala.collection.mutable

class ExceptionMux[T <: Pipeline](lastInsertIdx: Int) extends Plugin[T] {
  case class ExceptionSource(valid: Bool, badVAddr: UInt, excCode: Int, priority: Int)
  private val excMap = mutable.HashMap[Stage, mutable.ArrayBuffer[ExceptionSource]]()
  def addExceptionSource(
      stage: Stage,
      excCode: Int,
      valid: Bool,
      badVAddr: UInt = null,
      priority: Int = 0
  ) {
    excMap.getOrElseUpdate(stage, mutable.ArrayBuffer()) +=
      ExceptionSource(valid, badVAddr, excCode, priority)
  }

  override def build(pipeline: T): Unit = pipeline plug new Area {
    // insert exception signals for handler, even if no exception happens
    var firstExcStage = lastInsertIdx
    var firstVAStage = lastInsertIdx
    excMap.foreach { case (stage, excList) =>
      if (excList.nonEmpty)
        firstExcStage = math.min(firstExcStage, pipeline.indexOf(stage))
      excList.sortBy(_.priority).foreach { excSrc =>
        stage.output(EXCEPTION_OCCURRED).setWhen(excSrc.valid)
        // 如果先前已经有异常，那么忽略本阶段产生的异常
        when(!stage.input(EXCEPTION_OCCURRED) && excSrc.valid) {
          stage.output(EXCEPTION_CODE) := excSrc.excCode
          // badVA仅在需要时修改
          if (excSrc.badVAddr != null) {
            firstVAStage = math.min(firstVAStage, pipeline.indexOf(stage))
            stage.output(BAD_VADDR) := excSrc.badVAddr
          }
        }
      }
    }
    // 初值在需要的阶段insert
    pipeline.stages(firstExcStage).insert(EXCEPTION_OCCURRED) := False
    pipeline.stages(firstExcStage).insert(EXCEPTION_CODE).assignDontCare()
    pipeline.stages(firstVAStage).insert(BAD_VADDR).assignDontCare()
  }
}
