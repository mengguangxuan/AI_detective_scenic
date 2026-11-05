package services.sceneproduction.api
import cats.effect.IO
import java.sql.Connection
import java.time.Instant
import services.sceneproduction.objects.*
import services.sceneproduction.tables.SceneProductionTable.SceneProductionTable
import services.sceneproduction.utils.SceneProductionValidation
import services.system.api.APIMessage
import services.system.objects.*
final case class SaveSceneDraftMessage(userId: UserId,productionId: SceneProductionId,input: SceneAuthorInput) extends APIMessage[SceneProductionRecord]:
  def plan(c: Connection): IO[SceneProductionRecord] = for
    _ <- SceneProductionValidation.input(input)
    existing <- SceneProductionTable.lock(c,productionId)
    result <- existing match
      case None =>
        val row=SceneProductionRecord(productionId,userId,input,SceneProductionPhase.Draft,Instant.now().toString,None)
        SceneProductionTable.insert(c,row).as(row)
      case Some(row) => SceneProductionValidation.owned(Some(row),userId).flatMap { owned =>
        if owned.input==input then IO.pure(owned)
        else if owned.phase!=SceneProductionPhase.Draft then IO.raiseError(GameError(ErrorCode.ConflictingContent,"制作已开始，请复制为新草稿后修改资料。"))
        else SceneProductionTable.updateInput(c,productionId,input).as(owned.copy(input=input))
      }
  yield result
