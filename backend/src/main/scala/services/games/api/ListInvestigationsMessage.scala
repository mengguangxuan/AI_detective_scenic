package services.games.api

import cats.effect.IO
import java.sql.Connection
import services.games.objects.ReasoningEndingMode
import services.games.objects.ReasoningStatus
import services.games.objects.SavedInvestigationStatus
import services.games.objects.apiTypes.SavedInvestigationView
import services.games.tables.SessionTable.SessionTable
import services.system.api.APIMessage
import services.system.objects.UserId

final case class ListInvestigationsMessage(userId: UserId) extends APIMessage[Vector[SavedInvestigationView]]:
  override def plan(connection: Connection): IO[Vector[SavedInvestigationView]] =
    SessionTable.listOwned(connection,userId).map(_.map { row =>
      val status = row.reasoning.ending match
        case Some(ending) if ending.mode == ReasoningEndingMode.Solved => SavedInvestigationStatus.Solved
        case Some(_) => SavedInvestigationStatus.Revealed
        case None if row.legacySubmitted => SavedInvestigationStatus.LegacySubmitted
        case None if row.reasoning.attempts.lastOption.exists(_.status == ReasoningStatus.Established) => SavedInvestigationStatus.Established
        case None => SavedInvestigationStatus.Investigating
      SavedInvestigationView(row.sessionId,row.caseId,row.revision,row.title,row.sequence,row.updatedAtMillis,status)
    })
