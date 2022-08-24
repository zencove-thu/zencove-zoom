package zencove.builder

import scala.reflect.ClassTag

trait ServiceProvider {
  def service[T](implicit tag: ClassTag[T]): T
  def serviceExist[T](implicit tag: ClassTag[T]): Boolean
  def service[T](clazz: Class[T]): T
  def serviceExist[T](clazz: Class[T]): Boolean
}
