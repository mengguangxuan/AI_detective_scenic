package services.sceneproduction.api
import cats.effect.IO
import services.sceneproduction.objects.SceneProductionId
import services.sceneproduction.tables.SceneProductionTable.SceneProductionTable
import services.system.api.DatabaseSession

// Explicit local operator request; never inferred from stored failures or HTTP input.
object ResumeSceneOnStartup:
  def run(worker: SceneProductionWorker,databaseUrl: String,request: Option[String]): IO[Unit] = request match
    case None => IO.unit
    case Some(raw) =>
      val parts=raw.split(":",-1)
      require(parts.length==2 && parts(0).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") && parts(1).matches("[0-9]{1,2}"),"Invalid explicit scene resume request")
      for
        row <- DatabaseSession.connection(databaseUrl).use(c=>SceneProductionTable.find(c,SceneProductionId(parts(0))))
        record <- IO.fromOption(row)(new IllegalArgumentException("Requested scene task does not exist"))
        _ <- worker.resume(record,parts(1).toInt)
      yield ()
