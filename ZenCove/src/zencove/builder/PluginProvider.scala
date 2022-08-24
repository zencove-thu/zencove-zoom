package zencove.builder

import scala.reflect.ClassTag
import scala.collection.mutable
import spinal.core.Component

trait PluginProvider extends ServiceProvider {
  type T <: Pipeline
  val plugins = mutable.ArrayBuffer[Plugin[T]]()
  var globalCtx: PluginProvider = this
  val withSetup = true
  // try some reflect magic
  override def service[T](implicit tag: ClassTag[T]) = {
    val clazz = tag.runtimeClass
    val filtered = plugins.filter(o => clazz.isAssignableFrom(o.getClass))
    assert(filtered.length == 1, s"??? ${clazz.getName}")
    filtered.head.asInstanceOf[T]
  }
  override def service[T](clazz: Class[T]) = {
    val filtered = plugins.filter(o => clazz.isAssignableFrom(o.getClass))
    assert(filtered.length == 1, s"??? ${clazz.getName}")
    filtered.head.asInstanceOf[T]
  }
  override def serviceExist[T](implicit tag: ClassTag[T]) =
    plugins.exists(o => tag.runtimeClass.isAssignableFrom(o.getClass))
  override def serviceExist[T](clazz: Class[T]) =
    plugins.exists(o => clazz.isAssignableFrom(o.getClass))
  def globalService[T](implicit tag: ClassTag[T]) = globalCtx.service(tag)

  def setupPlugins(): Unit = {
    plugins.foreach { p =>
      // Put the given plugin as a child of the current component
      p.parentScope = Component.current.dslBody
      p.reflectNames()
    }
    // Setup plugins
    if (withSetup) plugins.foreach(p => if (p.withSetup) p.setup(this.asInstanceOf[T]))
  }

  def buildPlugins(): Unit = {
    // Build plugins
    plugins.foreach(_.build(this.asInstanceOf[T]))
  }
}
