package services.cases

import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.{Json,parser}
import io.circe.syntax.*
import java.nio.file.{Files,Paths}
import java.util.UUID
import munit.FunSuite
import services.cases.api.{GetDialogueKnowledgeMessage,ImportCaseMessage}
import services.cases.objects.*
import services.cases.tables.CaseRevisionTable.CaseRevisionTable
import services.cases.utils.FixtureLoader
import services.games.api.{AskWitnessMessage,StartGameMessage}
import services.games.objects.{InterviewContext,InterviewReplyMode}
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.{DatabaseSchema,DatabaseSession}
import services.system.objects.*

class CharacterConfigurationSuite extends FunSuite:
  private val source = parser.parse(Files.readString(Paths.get("../docs/fixtures/browser_investigation.json"))).toOption.get
  private def changeCharacter(source: Json, id: String)(change: Json => Json): Json =
    source.mapObject(fields => fields.add("characters",Json.fromValues(fields("characters").get.asArray.get.map(p =>
      if p.hcursor.get[String]("id").contains(id) then change(p) else p))))
  private def error(source: Json, path: String): Unit =
    val result = FixtureLoader.decode(source.noSpaces).left.toOption.get
    assertEquals(result.code,ErrorCode.InvalidValue)
    assert(result.publicMessage.contains(path),result.publicMessage)
  private def tx[A](url: String)(f: java.sql.Connection => IO[A]): IO[A] =
    DatabaseSession.connection(url).use(c => DatabaseSession.transaction(c)(f(c)))

  test("new authoring separates personal data, interaction bindings and police selection") {
    val record = FixtureLoader.decode(source.noSpaces).toOption.get
    assertEquals(record.content.publicPersons.size,13)
    assertEquals(record.content.witnesses.size,8)
    assertEquals(record.content.police.get.id,WitnessId("melchett"))
    assert(record.content.witnesses.find(_.id == WitnessId("clement")).get.dossier.knowledge.exists(_.contains("阿博特")))
    val mixed = changeCharacter(source,"clement")(_.mapObject(_.add("point",Json.fromString("point_clement"))))
    error(mixed,"人物资料")
    val missing = source.hcursor.downField("investigation").downField("interviews").downArray.downField("person")
      .withFocus(_ => Json.fromString("nonexistent")).top.get
    error(missing,"investigation.interviews[0].person")
    val duplicate = source.hcursor.downField("characters").withFocus(x => Json.fromValues(x.asArray.get :+ x.asArray.get.head)).top.get
    error(duplicate,"characters")
    val mixedBinding = source.hcursor.downField("investigation").downField("interviews").downArray
      .withFocus(_.mapObject(_.add("knowledge",Json.arr(Json.fromString("wrong place"))))).top.get
    assert(FixtureLoader.decode(mixedBinding.noSpaces).isLeft)
  }

  test("police disclosures obey the same ownership rules and cannot name another actor's route") {
    val wrong = source.hcursor.downField("investigation").downField("interviews").withFocus(x =>
      Json.fromValues(x.asArray.get.map(p =>
        if p.hcursor.get[String]("id").contains("melchett") then
          p.hcursor.downField("answers").downArray.downField("routes").withFocus(_ => Json.arr(Json.fromString("route_clement_habit"))).top.get
        else p))).top.get
    error(wrong,"investigation.police.answers[0].routes")
    val duplicate = source.hcursor.downField("investigation").downField("interviews").withFocus(x =>
      Json.fromValues(x.asArray.get :+ x.asArray.get.head.mapObject(_.add("id",Json.fromString("second_identity"))))).top.get
    error(duplicate,"investigation.interviews.person")
  }

  test("old flat profiles and frozen contexts decode and retain their original JSON shape") {
    val flat = parser.parse("""{"id":"old_actor","point":"point_old","name":"旧证人","background":"旧背景","voice":"简短","answers":[],"knowledge":["旧记忆"],"privateContext":["旧内情"],"policy":["旧策略"]}""").toOption.get
    val profile = flat.as[WitnessProfile].toOption.get
    assertEquals(profile.asJson,flat)
    assertEquals(profile.dossier.privateContext,Vector("旧内情"))
    val minimal = flat.mapObject(_.remove("knowledge").remove("privateContext").remove("policy"))
    assertEquals(minimal.as[WitnessProfile].toOption.get.dossier,DialogueDossier(Vector.empty,Vector.empty,Vector.empty))
    val context = Json.obj("profile" -> flat,"evidence" -> Json.arr(),"history" -> Json.arr(),
      "caseId" -> Json.fromString("old_case"),"revision" -> Json.obj("value" -> Json.fromInt(3)))
    val frozen = context.as[InterviewContext].toOption.get
    assertEquals(frozen.responseVersion,1)
    assertEquals(frozen.profile.asJson,flat)
    val oldPolice = parser.parse("""{"point":"point_police","name":"旧警方","background":"旧说明"}""").toOption.get.as[PoliceContact].toOption.get
    assertEquals(oldPolice.id,WitnessId("melchett"))
    assert(oldPolice.dossier.knowledge.isEmpty)
  }

  test("another case and another police identity work from configuration with revision-frozen dossiers") {
    val url = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val path = Files.createTempFile("character-case-",".json")
    val actorPrivate = "OTHER_CASE_ACTOR_PRIVATE"
    val policePrivate = "OTHER_CASE_POLICE_PRIVATE"
    val dossier = (secret: String) => Json.obj("knowledge" -> Json.arr(Json.fromString("只属于新案件的本人记忆")),
      "privateContext" -> Json.arr(Json.fromString(secret)),"policy" -> Json.arr(Json.fromString("只说明本人知道的事")))
    val newPeople = changeCharacter(changeCharacter(source,"clement")(_.mapObject(_.add("name",Json.fromString("新案证人")).add("dossier",dossier(actorPrivate)))),"melchett")(
      _.mapObject(_.add("name",Json.fromString("周督察")).add("dossier",dossier(policePrivate))))
    val changed = newPeople.mapObject(_.add("id",Json.fromString("another_case")).add("revision",Json.obj("value" -> Json.fromInt(1))))
      .hcursor.downField("investigation").downField("interviews").withFocus(x => Json.fromValues(x.asArray.get.map(p =>
        if p.hcursor.get[String]("id").contains("melchett") then p.mapObject(_.add("id",Json.fromString("inspector_zhou"))) else p))).top.get
      .hcursor.downField("investigation").downField("police").downField("witness").withFocus(_ => Json.fromString("inspector_zhou")).top.get
    val owner = UserId("new-case-owner"); val id = SessionId(UUID.randomUUID().toString)
    val model = new ChatCompletionClient:
      override def complete(input: ChatCompletionInput): IO[String] = IO {
        assert(input.user.contains("周督察"))
        assert(input.user.contains(policePrivate))
        assert(!input.user.contains(actorPrivate))
        """{"reply":"我会按当前登记材料说明。","disclosureIds":[]}"""
      }
    (for
      _ <- IO.blocking(Files.writeString(path,changed.noSpaces))
      _ <- DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c))
      imported <- tx(url)(ImportCaseMessage(path).plan)
      repeated <- tx(url)(ImportCaseMessage(path).plan)
      _ = assertEquals(repeated,imported)
      _ <- tx(url)(StartGameMessage(owner,id,CaseId("another_case"),Revision(1)).plan)
      first <- tx(url)(GetDialogueKnowledgeMessage(CaseId("another_case"),Revision(1),WitnessId("clement")).plan)
      _ = assertEquals(first.privateContext,Vector(actorPrivate))
      _ <- IO.blocking(Files.writeString(path,changeCharacter(changed,"clement")(_.mapObject(_.add("dossier",dossier("CHANGED_SOURCE")))).noSpaces))
      stillFrozen <- tx(url)(GetDialogueKnowledgeMessage(CaseId("another_case"),Revision(1),WitnessId("clement")).plan)
      _ = assertEquals(stillFrozen,first)
      conflicting <- tx(url)(ImportCaseMessage(path).plan).attempt
      _ = assertEquals(conflicting.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.ConflictingContent))
      _ <- AskWitnessMessage(owner,id,RequestId("police-question"),Sequence(0),WitnessId("inspector_zhou"),"你知道什么？",Set.empty).run(url,model)
      turns <- tx(url)(InterviewTurnTable.list(_,id))
      _ = assertEquals(turns.last.mode,InterviewReplyMode.Answered)
      stored <- tx(url)(CaseRevisionTable.find(_,CaseId("another_case"),Revision(1)))
      _ = assertEquals(stored.get,imported)
    yield ()).guarantee(IO.blocking(Files.deleteIfExists(path)).void).unsafeRunSync()
  }
