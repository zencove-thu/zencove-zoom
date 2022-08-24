package zencove.builder

import spinal.core._

trait Plugin[T <: Pipeline] extends Nameable {
  setName(this.getClass.getSimpleName.replace("$", ""))
  private[builder] var withSetup = true
  def noSetup() = { withSetup = false; this }

  // Used to setup things with other plugins
  def setup(pipeline: T): Unit = {}

  //Used to flush out the required hardware (called after setup)
  def build(pipeline: T): Unit

  implicit class implicitsStage(stage: Stage) {
    def plug[T <: Area](area: T): T = {
      area.setCompositeName(stage, getName()).reflectNames(); area
    }
  }
  implicit class implicitsPipeline(stage: Pipeline) {
    def plug[T <: Area](area: T) = {
      area.setName(getName()).reflectNames(); area
    }
  }
}
