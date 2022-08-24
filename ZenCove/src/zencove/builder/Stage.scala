package zencove.builder

import spinal.core._
import scala.collection.mutable
import scala.language.existentials

class Stage extends Area {

  /** 将语句块在当前when等条件块外执行。
    *
    * @param that
    *   要执行的语句块
    * @return
    *   语句块返回值
    */
  private def outsideCondScope[T](that: => T): T = {
    // Get the head of the current component symboles tree (AST in other words)
    val body = Component.current.dslBody
    // Now all access to the SpinalHDL API will be append to it (instead of the current context)
    val ctx = body.push()
    // Empty the symbole tree (but keep a reference to the old content)
    val swapContext = body.swap()
    // Execute the block of code (will be added to the recently empty body)
    val ret = that
    // Restore the original context in which this function was called
    ctx.restore()
    // append the original symboles tree to the modified body
    swapContext.appendBack()
    // return the value returned by that
    ret
  }

  /** 获取当前stage信号input值。
    *
    * @param key
    * @return
    *   信号本身
    */
  def input[T <: Data](key: Stageable[T]): T = {
    inputs
      .getOrElseUpdate(
        key.asInstanceOf[Stageable[Data]],
        outsideCondScope {
          val input, inputDefault = key()
          inputsDefault(key.asInstanceOf[Stageable[Data]]) = inputDefault
          input := inputDefault
          input.setPartialName(this, key.getName())
        }
      )
      .asInstanceOf[T]
  }

  /** 获取当前stage信号的output值。可以用作左值，修改一个流水线信号的值。
    *
    * @param key
    *   信号key
    * @return
    *   信号本身
    */
  def output[T <: Data](key: Stageable[T]): T = {
    outputs
      .getOrElseUpdate(
        key.asInstanceOf[Stageable[Data]],
        outsideCondScope {
          val output, outputDefault = key()
          outputsDefault(key.asInstanceOf[Stageable[Data]]) = outputDefault
          output := outputDefault
          output //.setPartialName(this,"output_" + key.getName())
        }
      )
      .asInstanceOf[T]
  }

  /** 获取当前stage对应信号，以插入某个值。一个信号的值只能在一个stage插入，但可以采用output进行修改。
    *
    * @param key
    *   信号key
    * @return
    *   key绑定的信号
    */
  def insert[T <: Data](key: Stageable[T]): T = inserts
    .getOrElseUpdate(key.asInstanceOf[Stageable[Data]], outsideCondScope(key()))
    .asInstanceOf[T] //.setPartialName(this,key.getName())
//  def apply[T <: Data](key : Stageable[T]) : T = ???

  val arbitration = new Area {
    //user settable, stuck the instruction, should only be set by the instruction itself
    // 暂停本阶段的指令，应当由当前指令设置
    val haltItself = False
    //When settable, stuck the instruction, should only be set by something else than the stucked instruction
    // 跨阶段暂停某条指令
    val haltByOther = False
    //When settable, unschedule the instruction as if it was never executed (no side effect)
    // 移除本条指令，不影响流水线
    val removeIt = False
    //When settable, unschedule the current instruction
    // 冲刷流水线，当前和之后所有指令均失效
    val flushIt = False
    //When settable, unschedule instruction above in the pipeline
    // 冲刷流水线，之后所有指令均失效（当前指令仍有效）
    val flushNext = False
    //Inform if a instruction is in the current stage
    // REG。当前stage目前是有效指令
    val isValid = Bool
    // REG。当前stage在指令进入时是valid
    val isValidOnEntry = Bool
    //Inform if the instruction is stuck (haltItself || isStuckByOthers)
    val isStuck = Bool
    val notStuck = !isStuck
    //Inform if the instruction is stuck by sombody else
    // haltByOther或被后面的阶段压住
    val isStuckByOthers = Bool
    //Inform if the instruction is going to be unschedule the current cycle
    // removeIt的别名
    def isRemoved = removeIt
    //Inform if the instruction is flushed (flushAll set in the current or subsequents stages)
    val isFlushed = Bool
    //Inform if the instruction is going somewere else (next stage or unscheduled)
    // 此阶段可以流动
    val isMoving = Bool
    //Inform if the current instruction will go to the next stage the next cycle (isValid && !isStuck && !removeIt)
    // 有效的指令执行完一个阶段
    val isFiring = Bool

    val isValidNotStuck = Bool
  }

  private[builder] val inputs = mutable.LinkedHashMap[Stageable[Data], Data]()
  private[builder] val outputs = mutable.LinkedHashMap[Stageable[Data], Data]()
  private[builder] val signals = mutable.LinkedHashMap[Stageable[Data], Data]()
  private[builder] val inserts = mutable.LinkedHashMap[Stageable[Data], Data]()

  private[builder] val inputsDefault =
    mutable.LinkedHashMap[Stageable[Data], Data]()
  private[builder] val outputsDefault =
    mutable.LinkedHashMap[Stageable[Data], Data]()

  private[builder] val dontSample =
    mutable.LinkedHashMap[Stageable[_], mutable.ArrayBuffer[Bool]]()

  /** 为信号添加一条don't sample条件，一个信号在任意don't sample条件成立时都不要让当前阶段寄存此信号。
    *
    * @param s
    *   信号key
    * @param cond
    *   要添加的条件
    */
  def dontSampleStageable(s: Stageable[_], cond: Bool): Unit = {
    dontSample.getOrElseUpdate(s, mutable.ArrayBuffer[Bool]()) += cond
  }

  /** 设置当前stage信号input的reset值。即流水线寄存器的reset值。
    *
    * @param stageable
    *   对应信号key
    * @param initValue
    *   寄存器reset值
    * @return
    */
  def inputInit[T <: BaseType](stageable: Stageable[T], initValue: T) =
    Component.current.addPrePopTask(() =>
      inputsDefault(stageable.asInstanceOf[Stageable[Data]])
        .asInstanceOf[T]
        .getDrivingReg
        .init(initValue)
    )
}
