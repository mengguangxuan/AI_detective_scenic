package services.games.api

import cats.effect.{IO,IOApp}
import cats.syntax.all.*
import io.circe.Json
import io.circe.syntax.*
import io.circe.parser.parse
import java.nio.file.Paths
import services.games.tables.InterviewRequestTable.InterviewRequestTable
import services.games.tables.InterviewTurnTable.InterviewTurnTable
import services.games.utils.InterviewRules
import services.system.api.DatabaseSession

// Local read-only audit. Stop WebDemo before running; never prints credentials or private dossiers.
object InspectInterviewEvidence extends IOApp.Simple:
  override def run: IO[Unit] =
    val platform=if System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows") then "windows" else "linux"
    val database=Paths.get("target","web-demo",platform,"db-v1").toAbsolutePath.normalize()
    val limit=Option(System.getenv("DETECTIVE_AUDIT_LIMIT")).flatMap(_.toIntOption).getOrElse(12).max(1).min(50)
    DatabaseSession.connection(s"jdbc:h2:file:$database;IFEXISTS=TRUE;ACCESS_MODE_DATA=r").use { connection =>
      for
        requests <- InterviewRequestTable.recent(connection,limit)
        _ <- requests.traverse_ { request =>
          for
            turns <- InterviewTurnTable.list(connection,request.sessionId)
            turn=turns.find(_.requestId == request.requestId)
            input=parse(InterviewRules.prompt(request).user).toOption.get
            sent=input.hcursor.get[Vector[Json]]("shownEvidence").getOrElse(Vector.empty)
            selected=sent.filter(j => j.hcursor.get[String]("id").exists(id => request.shownClues.exists(_.value == id)))
            report=Json.obj("sessionId" -> request.sessionId.asJson,"requestId" -> request.requestId.asJson,
              "revision" -> request.context.revision.asJson,"sequence" -> request.expectedSequence.asJson,
              "promptVersion" -> request.context.promptVersion.asJson,"witness" -> request.witness.asJson,
              "question" -> request.question.asJson,"selectedIds" -> request.shownClues.asJson,
              "selectedInModelInput" -> selected.asJson,
              "presentedThisTurn" -> input.hcursor.get[Vector[Json]]("presentedThisTurn").toOption.asJson,
              "missingSelectedIds" -> request.shownClues.filterNot(id => selected.exists(_.hcursor.get[String]("id").contains(id.value))).asJson,
              "reply" -> turn.map(_.reply).asJson,"mode" -> turn.map(_.mode).asJson)
            _ <- IO.println(report.noSpaces)
          yield ()
        }
      yield ()
    }
