package services.sceneproduction.routes
import cats.effect.IO
import io.circe.{Json,Decoder}
import io.circe.syntax.*
import services.sceneproduction.api.*
import services.sceneproduction.objects.*
import services.system.api.{RegisteredAPIMessage,DatabaseSession}
import services.system.objects.UserId
object SceneProductionRoutes:
  private def transaction[A](url: String)(run: java.sql.Connection=>IO[A]): IO[A] = DatabaseSession.connection(url).use(c=>DatabaseSession.transaction(c)(run(c)))
  def apiMessages(worker: SceneProductionWorker): Vector[RegisteredAPIMessage] = Vector(
    RegisteredAPIMessage.authenticatedWorkflow[SaveSceneDraftMessage,SaveSceneDraftInput,SceneProductionView](Set("productionId","input"),Set.empty){(input,user,url)=>
      transaction(url)(SaveSceneDraftMessage(user,input.productionId,input.input).plan).flatMap(worker.view)
    }.copy(maxRequestBytes=160000),
    RegisteredAPIMessage.authenticatedWorkflow[ListSceneProductionsMessage,Json,Vector[SceneProductionView]](Set.empty,Set.empty){(_,user,url)=>
      transaction(url)(ListSceneProductionsMessage(user).plan).flatMap(rows=>rows.foldLeft(IO.pure(Vector.empty[SceneProductionView]))((acc,row)=>acc.flatMap(xs=>worker.status(row).map(status=>xs :+ SceneProductionView(row.id,row.input,row.phase,row.createdAt,row.savedAt,status,None)))))
    },
    RegisteredAPIMessage.authenticatedWorkflow[GetSceneProductionMessage,SceneProductionRef,SceneProductionView](Set("productionId"),Set.empty){(input,user,url)=>
      transaction(url)(GetSceneProductionMessage(user,input.productionId).plan).flatMap(worker.view)
    },
    RegisteredAPIMessage.authenticatedWorkflow[StartSceneProductionMessage,SceneProductionRef,SceneProductionView](Set("productionId"),Set.empty){(input,user,url)=>for
      row <- transaction(url)(StartSceneProductionMessage(user,input.productionId).plan)
      _ <- worker.start(row)
      result <- worker.view(row)
    yield result},
    RegisteredAPIMessage.authenticatedWorkflow[ResumeSceneProductionMessage,ResumeSceneProductionInput,SceneProductionView](Set("productionId","expectedCalls"),Set.empty){(input,user,url)=>for
      row <- transaction(url)(ResumeSceneProductionMessage(user,input.productionId).plan)
      _ <- worker.resume(row,input.expectedCalls)
      result <- worker.view(row)
    yield result},
    RegisteredAPIMessage.authenticatedWorkflow[ConfirmSceneLayoutMessage,SceneProductionRef,SceneProductionView](Set("productionId"),Set.empty){(input,user,url)=>for
      row <- transaction(url)(ConfirmSceneLayoutMessage(user,input.productionId).plan)
      _ <- worker.confirmLayout(row)
      result <- worker.view(row)
    yield result},
    RegisteredAPIMessage.authenticatedWorkflow[SaveSceneArtifactMessage,SceneProductionRef,SceneProductionView](Set("productionId"),Set.empty){(input,user,url)=>for
      row <- transaction(url)(GetSceneProductionMessage(user,input.productionId).plan)
      _ <- worker.save(row)
      saved <- transaction(url)(SaveSceneArtifactMessage(user,input.productionId).plan)
      result <- worker.view(saved)
    yield result}
  )
