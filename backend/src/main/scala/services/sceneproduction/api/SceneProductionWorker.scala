package services.sceneproduction.api
import cats.effect.{IO,Resource}
import cats.effect.unsafe.IORuntime
import services.models.api.{ChatCompletionClient,SceneModelBridge}
import java.nio.file.Path
import services.sceneproduction.objects.*
import services.sceneproduction.utils.LocalSceneProductionWorker
trait SceneProductionWorker:
  def start(row: SceneProductionRecord): IO[Unit]
  def resume(row: SceneProductionRecord,expectedCalls: Int): IO[Unit] = IO.raiseError(new IllegalStateException("Worker cannot resume"))
  def status(row: SceneProductionRecord): IO[SceneProductionStatus]
  def confirmLayout(row: SceneProductionRecord): IO[Unit]
  def save(row: SceneProductionRecord): IO[Unit]
  def preview(row: SceneProductionRecord): IO[Option[String]]
  final def view(row: SceneProductionRecord): IO[SceneProductionView] = for
    current <- status(row)
    url <- if current.stage=="passed" then preview(row) else IO.pure(None)
  yield SceneProductionView(row.id,row.input,row.phase,row.createdAt,row.savedAt,current,url)
object SceneProductionWorker:
  def local(projectRoot: Path = java.nio.file.Paths.get("..").toAbsolutePath.normalize()): SceneProductionWorker = new LocalSceneProductionWorker(projectRoot)

  def managed(client: ChatCompletionClient,projectRoot: Path = java.nio.file.Paths.get("..").toAbsolutePath.normalize())(using IORuntime): Resource[IO,SceneProductionWorker] =
    SceneModelBridge.resource(client).map(transport=>new LocalSceneProductionWorker(projectRoot,Some(transport)))
