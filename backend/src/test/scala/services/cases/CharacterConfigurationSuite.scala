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
import services.cases.tables.CaseTable.CaseTable
import services.cases.utils.FixtureLoader
import services.games.api.{AskWitnessMessage,StartGameMessage}
import services.games.objects.{InterviewContext,InterviewReplyMode}
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.models.api.ChatCompletionClient
import services.models.objects.ChatCompletionInput
import services.system.api.{DatabaseSchema,DatabaseSession}
import services.system.objects.*
import services.cases.objects.dialogue.{DialogueDossier, WitnessProfile}

class CharacterConfigurationSuite extends FunSuite:
  private val source = parser.parse(Files.readString(Paths.get("../docs/fixtures/investigation_base.json"))).toOption.get
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
    assertEquals(record.content.publicPersons.size,14)
    assertEquals(record.content.witnesses.size,10)
    assertEquals(record.content.police.get,WitnessId("melchett"))
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
    error(wrong,".answers[0].routes")
    val duplicate = source.hcursor.downField("investigation").downField("interviews").withFocus(x =>
      Json.fromValues(x.asArray.get :+ x.asArray.get.head.mapObject(_.add("id",Json.fromString("second_identity"))))).top.get
    error(duplicate,"investigation.interviews.person")
  }


  test("another case and another police identity work from configuration with caseRecord-frozen dossiers") {
    val url = s"jdbc:h2:mem:${UUID.randomUUID()};DB_CLOSE_DELAY=-1"
    val path = Files.createTempFile("character-case-",".json")
    val actorPrivate = "OTHER_CASE_ACTOR_PRIVATE"
    val policePrivate = "OTHER_CASE_POLICE_PRIVATE"
    val dossier = (secret: String) => Json.obj("knowledge" -> Json.arr(Json.fromString("只属于新案件的本人记忆")),
      "privateContext" -> Json.arr(Json.fromString(secret)),"policy" -> Json.arr(Json.fromString("只说明本人知道的事")))
    val newPeople = changeCharacter(changeCharacter(source,"clement")(_.mapObject(_.add("name",Json.fromString("新案证人")).add("dossier",dossier(actorPrivate)))),"melchett")(
      _.mapObject(_.add("name",Json.fromString("周督察")).add("dossier",dossier(policePrivate))))
    val changed = newPeople.mapObject(_.add("id",Json.fromString("another_case")))
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
      _ <- tx(url)(StartGameMessage(owner,id,CaseId("another_case")).plan)
      first <- tx(url)(GetDialogueKnowledgeMessage(CaseId("another_case"),WitnessId("clement")).plan)
      _ = assertEquals(first.privateContext,Vector(actorPrivate))
      _ <- IO.blocking(Files.writeString(path,changeCharacter(changed,"clement")(_.mapObject(_.add("dossier",dossier("CHANGED_SOURCE")))).noSpaces))
      stillFrozen <- tx(url)(GetDialogueKnowledgeMessage(CaseId("another_case"),WitnessId("clement")).plan)
      _ = assertEquals(stillFrozen,first)
      conflicting <- tx(url)(ImportCaseMessage(path).plan).attempt
      _ = assertEquals(conflicting.left.toOption.collect { case e: GameError => e.code },Some(ErrorCode.ConflictingContent))
      _ <- AskWitnessMessage(owner,id,RequestId("police-question"),Sequence(0),WitnessId("inspector_zhou"),"你知道什么？",Set.empty).run(url,model)
      turns <- tx(url)(InterviewTurnTable.list(_,id))
      _ = assertEquals(turns.last.mode,InterviewReplyMode.Answered)
      stored <- tx(url)(CaseTable.find(_,CaseId("another_case")))
      _ = assertEquals(stored.get,imported)
    yield ()).guarantee(IO.blocking(Files.deleteIfExists(path)).void).unsafeRunSync()
  }
