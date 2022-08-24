package zencove.builder

import scala.collection.mutable
import spinal.core._
import spinal.lib._
import scala.reflect.ClassTag

trait MultiPipeline extends Pipeline {
  val pipelines = mutable.ArrayBuffer[Pipeline]()
  override def setupPlugins(): Unit = {
    super.setupPlugins()
    pipelines.foreach(_.setupPlugins())
  }

  override def buildPlugins(): Unit = {
    super.buildPlugins()
    pipelines.foreach(_.buildPlugins())
  }
  override def connectStages(): Unit = {
    if (stages.nonEmpty) super.connectStages()
    pipelines.foreach { p =>
      println("Pipeline " + p.stages.map(_.getName()).mkString(" - "))
      p.connectStages()
    }
  }
  def addPipeline(pipeline: Pipeline) {
    pipeline.globalCtx = this
    pipelines += pipeline
  }
}
