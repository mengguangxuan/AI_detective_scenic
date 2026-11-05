package services.sceneproduction.utils
import cats.effect.IO
import services.sceneproduction.objects.*
import services.system.objects.*
private[sceneproduction] object SceneProductionValidation:
  def input(value: SceneAuthorInput): IO[Unit] = IO {
    if value.title.trim.isEmpty || value.title.length>120 || !value.targetPlaceId.matches("[a-zA-Z0-9_-]{1,80}") ||
      !value.fields.asObject.exists(_.nonEmpty) || value.fields.noSpaces.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>120000 then
      throw GameError(ErrorCode.InvalidValue,"请填写场景名称、有效场景编号与原始资料，资料最多120KB。")
  }
  def owned(record: Option[SceneProductionRecord],user: UserId): IO[SceneProductionRecord] =
    IO.fromOption(record.filter(_.owner==user))(GameError(ErrorCode.AccessDenied,"无权访问此场景制作记录。"))
