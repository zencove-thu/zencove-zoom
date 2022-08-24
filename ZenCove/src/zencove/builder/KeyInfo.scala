package zencove.builder

class KeyInfo {
  var insertStageId = Int.MaxValue
  var lastInputStageId = Int.MinValue
  var lastOutputStageId = Int.MinValue

  def addInputStageIndex(stageId: Int): Unit = {
    require(stageId >= insertStageId)
    lastInputStageId = Math.max(lastInputStageId, stageId)
    lastOutputStageId = Math.max(lastOutputStageId, stageId - 1)
  }

  def addOutputStageIndex(stageId: Int): Unit = {
    require(stageId >= insertStageId)
    lastInputStageId = Math.max(lastInputStageId, stageId)
    lastOutputStageId = Math.max(lastOutputStageId, stageId)
  }

  def setInsertStageId(stageId: Int) = {
    require(insertStageId == Int.MaxValue || insertStageId == stageId)
    insertStageId = stageId
  }
}
