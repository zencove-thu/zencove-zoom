package zencove.builder

import scala.collection.mutable
import spinal.core._
import spinal.lib._

trait WithStages {
  val stages = mutable.ArrayBuffer[Stage]()
  def indexOf(stage: Stage) = stages.indexOf(stage)
  def stageBefore(stage: Stage) = stages(indexOf(stage) - 1)
  def stageAfter(stage: Stage) = stages(indexOf(stage) + 1)
  def newStage(): Stage = {
    val s = new Stage()
    stages += s
    s
  }
  def connectStages(): Unit = {
    // Interconnect stages
    val inputOutputKeys = mutable.LinkedHashMap[Stageable[Data], KeyInfo]()
    val insertedStageable = mutable.Set[Stageable[Data]]()
    for (stageIndex <- 0 until stages.length; stage = stages(stageIndex)) {
      stage.inserts.keysIterator.foreach { signal =>
        try {
          inputOutputKeys.getOrElseUpdate(signal, new KeyInfo).setInsertStageId(stageIndex)
        } catch {
          case e: IllegalArgumentException => {
            val info = inputOutputKeys.getOrElseUpdate(signal, new KeyInfo)
            throw new Exception(s"cannot insert ${signal.getName()} at ${stages(stageIndex)
              .getName()}, already inserted at ${stages(info.insertStageId).getName()}")
          }
        }
      }
      stage.inserts.keysIterator.foreach(insertedStageable += _)
    }

    val missingInserts = mutable.Set[Stageable[Data]]()
    for (stageIndex <- 0 until stages.length; stage = stages(stageIndex)) {
      stage.inputs.keysIterator.foreach(key =>
        if (!insertedStageable.contains(key)) missingInserts += key
      )
      stage.outputs.keysIterator.foreach(key =>
        if (!insertedStageable.contains(key)) missingInserts += key
      )
    }

    if (missingInserts.nonEmpty) {
      throw new Exception(
        "Missing inserts : " + missingInserts.map(_.getName()).mkString(", ")
      )
    }

    for (stageIndex <- 0 until stages.length; stage = stages(stageIndex)) {
      stage.inputs.keysIterator.foreach { key =>
        try {
          inputOutputKeys
            .getOrElseUpdate(key, new KeyInfo)
            .addInputStageIndex(stageIndex)
        } catch {
          case e: IllegalArgumentException => {
            val info = inputOutputKeys.getOrElseUpdate(key, new KeyInfo)
            throw new Exception(s"${key.getName()} cannot be input at ${stages(stageIndex)
              .getName()}, inserted at ${stages(info.insertStageId).getName()}")
          }
        }
      }
      stage.outputs.keysIterator.foreach { key =>
        try {
          inputOutputKeys
            .getOrElseUpdate(key, new KeyInfo)
            .addOutputStageIndex(stageIndex)
        } catch {
          case e: IllegalArgumentException => {
            val info = inputOutputKeys.getOrElseUpdate(key, new KeyInfo)
            throw new Exception(s"${key.getName()} cannot be output at ${stages(stageIndex)
              .getName()}, inserted at ${stages(info.insertStageId).getName()}")
          }
        }
      }
    }

    for ((key, info) <- inputOutputKeys) {
      // Interconnect inputs -> outputs
      for (
        stageIndex <- info.insertStageId to info.lastOutputStageId;
        stage = stages(stageIndex)
      ) {
        stage.output(key)
        val outputDefault = stage.outputsDefault.getOrElse(key, null)
        assert(outputDefault != null)
        if (outputDefault != null) {
          outputDefault := stage.input(key)
        }
      }

      // Interconnect outputs -> inputs
      for (stageIndex <- info.insertStageId to info.lastInputStageId) {
        val stage = stages(stageIndex)
        stage.input(key)
        val inputDefault = stage.inputsDefault.getOrElse(key, null)
        assert(inputDefault != null)
        if (inputDefault != null) {
          if (stageIndex == info.insertStageId) {
            inputDefault := stage.inserts(key)
          } else {
            val stageBefore = stages(stageIndex - 1)
            // ???????????????????????????
            inputDefault := RegNextWhen(
              stageBefore.output(key),
              stage.dontSample
                .getOrElse(key, Nil)
                .foldLeft(!stage.arbitration.isStuck)(_ && !_)
            ).setName(
              s"${stageBefore.getName()}_to_${stage.getName()}_${key.getName()}"
            )
          }
        }
      }
    }

    //Arbitration
    for (stageIndex <- 0 until stages.length; stage = stages(stageIndex)) {
      // i+1???????????????????????????flushNext??????i???????????????????????????flushIt
      stage.arbitration.isFlushed := stages
        .drop(stageIndex + 1)
        .map(_.arbitration.flushNext)
        .orR || stages
        .drop(stageIndex)
        .map(_.arbitration.flushIt)
        .orR
      // ??????????????????isFlushed????????????removeIt
      stage.arbitration.removeIt setWhen stage.arbitration.isFlushed
    }

    for (stageIndex <- 0 until stages.length; stage = stages(stageIndex)) {
      // i+1??????????????????????????????????????????????????????????????????
      stage.arbitration.isStuckByOthers := stage.arbitration.haltByOther || stages
        .takeRight(stages.length - stageIndex - 1)
        .map(s => s.arbitration.isStuck /* && !s.arbitration.removeIt*/ )
        .orR
      // ?????????????????????????????????????????????
      stage.arbitration.isStuck := stage.arbitration.haltItself || stage.arbitration.isStuckByOthers
      stage.arbitration.isMoving := !stage.arbitration.isStuck && !stage.arbitration.removeIt
      stage.arbitration.isFiring := stage.arbitration.isValid && !stage.arbitration.isStuck && !stage.arbitration.removeIt
      stage.arbitration.isValidNotStuck := stage.arbitration.isValid && !stage.arbitration.isStuck
    }

    {
      // stage 0 ??????
      val stage = stages(0)
      stage.arbitration.isValid.setAsReg() init True
      stage.arbitration.isValidOnEntry := True
      // ????????????removeIt
      stage.arbitration.isValid clearWhen (stage.arbitration.removeIt)
      // ?????????????????????True
      stage.arbitration.isValid setWhen (!stage.arbitration.isStuck)
    }
    for (stageIndex <- 1 until stages.length) {
      val stageBefore = stages(stageIndex - 1)
      val stage = stages(stageIndex)
      stage.arbitration.isValid.setAsReg() init (False)
      stage.arbitration.isValidOnEntry.setAsReg() init (False)
      // removeIt????????????????????????
      when(!stage.arbitration.isStuck || stage.arbitration.removeIt) {
        stage.arbitration.isValid := False
      }
      // ????????????????????????removeIt??????????????????
      stage.arbitration.isValidOnEntry clearWhen (!stage.arbitration.isStuck)
      // ????????????remove???????????????????????????????????????
      when(
        !stageBefore.arbitration.isStuck && !stageBefore.arbitration.removeIt
      ) {
        stage.arbitration.isValid := stageBefore.arbitration.isValid
        stage.arbitration.isValidOnEntry := stageBefore.arbitration.isValid
      }
      // ?????????when??????????????????????????????
      // 1. ?????????????????????????????????????????????remove??????????????????
      // 2. ?????????????????????(1)????????????????????????????????????(2)???????????????remove?????????????????????isValid
      // ??????2.1???????????????????????????
    }
  }
}
