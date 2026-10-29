package services.system.api

import cats.effect.IO
import java.sql.Connection
import services.cases.tables.CaseRevisionTable.CaseRevisionTableInitializer
import services.games.tables.SessionTable.SessionTableInitializer
import services.games.tables.SessionRecordTable.SessionRecordTableInitializer
import services.games.tables.ReceiptTable.ReceiptTableInitializer
import services.games.tables.InterviewRequestTable.InterviewRequestTableInitializer
import services.games.tables.InterviewTurnTable.InterviewTurnTableInitializer
import services.games.tables.PoliceExchangeTable.PoliceExchangeTableInitializer

// Startup infrastructure; each table still owns its own DDL.
object DatabaseSchema:
  def initialize(connection: Connection): IO[Unit] =
    CaseRevisionTableInitializer.initialize(connection) *>
      SessionTableInitializer.initialize(connection) *>
      SessionRecordTableInitializer.initialize(connection) *>
      ReceiptTableInitializer.initialize(connection) *>
      InterviewRequestTableInitializer.initialize(connection) *>
      InterviewTurnTableInitializer.initialize(connection) *>
      PoliceExchangeTableInitializer.initialize(connection)
