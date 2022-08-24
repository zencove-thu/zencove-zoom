package zencove

import spinal.core._
import spinal.lib._

package object util {
  // implicit magic
  implicit def pimpData[T <: Data](data: DataPrimitives[T]) = new DataPimped[T](data)
  class DataPimped[T <: Data](pimped: DataPrimitives[T]) {
    def isAnyOf(rhs: T*) = rhs.map({ r => pimped === r }).orR
  }

  def prunePayloadRecursive(bundle: Bundle) {
    bundle.elements.foreach { case (name, ref) =>
      ref match {
        case s: Stream[_] => s.payload.setCompositeName(s)
        case f: Flow[_]   => f.payload.setCompositeName(f)
        case b: Bundle    => prunePayloadRecursive(b)
        case _            =>
      }
    }
  }
}
