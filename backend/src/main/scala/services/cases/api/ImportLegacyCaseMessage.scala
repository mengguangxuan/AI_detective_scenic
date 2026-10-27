package services.cases.api

import cats.effect.IO
import io.circe.Json
import io.circe.syntax.*
import java.nio.file.{Files,Path}
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.cases.utils.FixtureLoader
import services.system.api.APIMessage
import services.system.objects.*

// Migration-only input. Original runtime fields must match the supplied authoring revision.
final case class ImportLegacyCaseMessage(path: Path, legacy: Json) extends APIMessage[CaseRevisionRecord]:
  override def plan(connection: Connection): IO[CaseRevisionRecord] =
    for
      raw <- IO.blocking(Files.readString(path))
      expected <- IO.fromEither(FixtureLoader.decode(raw))
      actual <- IO.fromEither(normalize(expected).left.map(e => GameError(ErrorCode.InvalidValue,e.getMessage)))
      _ <- IO.raiseUnless(actual==expected)(GameError(ErrorCode.ConflictingContent,"旧案件快照与制作文件不一致，停止迁移。"))
      existing <- CaseRevisionTable.find(connection,expected.caseId,expected.revision)
      _ <- existing match
        case None => CaseRevisionTable.insert(connection,expected)
        case Some(saved) if saved==expected => IO.unit
        case Some(_) => IO.raiseError(GameError(ErrorCode.ConflictingContent,"目标案件修订已有不同内容。"))
    yield expected

  private def normalize(expected: CaseRevisionRecord): Either[io.circe.Error,CaseRevisionRecord] =
    val c=legacy.hcursor
    def withLabels(field: String, labels: Map[String,String]): io.circe.Decoder.Result[Json] =
      c.get[Vector[Json]](field).map(xs => xs.map { item =>
        val id=item.hcursor.get[String]("id").getOrElse("")
        item.deepMerge(Json.obj("label" -> Json.fromString(labels.getOrElse(id,id))))
      }.asJson)
    for
      id <- c.get[CaseId]("caseId")
      revision <- c.get[Revision]("revision")
      reducer <- c.downField("reducerVersion").get[String]("value")
      _ <- Either.cond(reducer=="fixture-v1",(),io.circe.DecodingFailure("Unknown legacy reducer",c.history))
      points <- withLabels("points",expected.content.points.map(p => p.id.value -> p.label).toMap)
      routes <- withLabels("routes",expected.content.routes.map(p => p.id.value -> p.label).toMap)
      operations <- withLabels("operations",expected.content.operations.map(p => p.id.value -> p.label).toMap)
      opened <- c.get[Json]("opened")
      unlocked <- c.get[Json]("unlocked")
      enriched = legacy.deepMerge(Json.obj(
        "points" -> points,"routes" -> routes,"operations" -> operations,
        "placeName" -> expected.content.placeName.asJson,
        "initialStoryTimeSeconds" -> expected.content.initialStoryTimeSeconds.asJson,
        "timeOriginLabel" -> expected.content.timeOriginLabel.asJson,
        "initiallyOpened" -> opened,"initiallyUnlocked" -> unlocked))
      content <- enriched.as[CaseContent]
    yield expected.copy(caseId=id,revision=revision,content=content)
