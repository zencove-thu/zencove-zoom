package zencove.builder

import spinal.core._

class Stageable[T <: Data](dataType: => T) extends HardType[T](dataType) with Nameable {
  setWeakName(this.getClass.getSimpleName.replace("$", ""))
}
