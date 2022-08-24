package zencove.builder

import spinal.core._
import spinal.lib._
import scala.collection.mutable
import scala.reflect.runtime.universe._
import scala.reflect.ClassTag

trait Pipeline extends WithStages with PluginProvider

trait PipelineAutoBuild extends Pipeline {
  Component.current.addPrePopTask { () =>
    setupPlugins()
    buildPlugins()
    connectStages()
  }
}
