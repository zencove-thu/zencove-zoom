package zencove.builder

import scala.reflect.ClassTag

/** 暂时没有用。
  *
  * @param seq
  */
class TraversableServiceProvider[T](seq: Traversable[T]) extends ServiceProvider {
  // try some reflect magic
  override def service[T](implicit tag: ClassTag[T]) = {
    val clazz = tag.runtimeClass
    val filtered = seq.filter(o => clazz.isAssignableFrom(o.getClass))
    assert(filtered.size == 1, s"??? ${clazz.getName}")
    filtered.head.asInstanceOf[T]
  }
  override def service[T](clazz: Class[T]) = {
    val filtered = seq.filter(o => clazz.isAssignableFrom(o.getClass))
    assert(filtered.size == 1, s"??? ${clazz.getName}")
    filtered.head.asInstanceOf[T]
  }
  override def serviceExist[T](implicit tag: ClassTag[T]) =
    seq.exists(o => tag.runtimeClass.isAssignableFrom(o.getClass))
  override def serviceExist[T](clazz: Class[T]) =
    seq.exists(o => clazz.isAssignableFrom(o.getClass))
}
