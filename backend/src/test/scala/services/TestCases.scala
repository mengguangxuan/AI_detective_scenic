package services

import cats.effect.IO
import java.sql.Connection
import services.cases.objects.*
import services.cases.tables.CaseTable.CaseTable
import services.games.api.{OpenInvestigationMessage, RegisterPlayableCaseMessage}
import services.games.objects.apiTypes.InvestigationSnapshot
import services.system.objects.*

// Test fixture only: publication still passes through the production capability checks.
object TestCases:
  def publish(connection: Connection, caseId: CaseId): IO[Unit] =
    CaseTable.find(connection,caseId).flatMap(value => IO.fromOption(value)(new IllegalStateException("Missing test case"))).flatMap(record =>
      RegisterPlayableCaseMessage(CaseReleaseRecord(caseId,"公开测试介绍",Vector.empty,record.content.sceneBundle)).plan(connection))
  def open(user: UserId, session: SessionId, caseId: CaseId)(connection: Connection): IO[InvestigationSnapshot] =
    publish(connection,caseId) *> OpenInvestigationMessage(user,session,caseId).plan(connection)
