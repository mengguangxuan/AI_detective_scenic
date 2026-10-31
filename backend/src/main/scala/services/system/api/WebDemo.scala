package services.system.api
import cats.effect.{IO,IOApp}
import cats.effect.unsafe.IORuntime
import java.nio.file.{Files,Paths}
import scala.concurrent.duration.*
import services.accounts.tables.LocalIdentityTable.LocalIdentityTableInitializer
import services.cases.api.ImportCaseMessage
import services.cases.objects.{CaseId, CaseReleaseRecord}
import services.games.api.RegisterPlayableCaseMessage
import services.system.objects.Revision
import services.models.api.ChatCompletionClient
object WebDemo extends IOApp.Simple:
  override def run: IO[Unit] =
    val platform = if System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows") then "windows" else "linux"
    val database = Paths.get("target","web-demo",platform,"db-v1").toAbsolutePath.normalize()
    val stopFile = database.resolveSibling("stop-requested")
    def waitForStop: IO[Unit] = IO.sleep(500.millis) *> IO.blocking(Files.exists(stopFile)).flatMap(stop => if stop then IO.unit else waitForStop)
    val url = s"jdbc:h2:file:$database;LOCK_TIMEOUT=10000;DB_CLOSE_DELAY=-1"
    given IORuntime = runtime
    for
      _ <- IO.blocking { Files.createDirectories(database.getParent); Files.deleteIfExists(stopFile) }
      _ <- DatabaseSession.connection(url).use { c => for
        _ <- DatabaseSchema.initialize(c)
        _ <- LocalIdentityTableInitializer.initialize(c)
        _ <- DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation_v20.json")).plan(c))
        _ <- DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c))
        _ <- DatabaseSession.transaction(c)(RegisterPlayableCaseMessage(CaseReleaseRecord(CaseId("vicarage_study"),Revision(21),
          "1930年的圣玛丽米德，上校死在牧师寓所的书房。走进保留现场，询问相关人物，对照证词与检查记录，提出你对案件的解释。",
          Vector("经典推理","现场探索","人物问询"),"study","5")).plan(c))
      yield () }
      modelPath = Option(System.getenv("DETECTIVE_MODEL_CONFIG")).filter(_.trim.nonEmpty)
        .map(Paths.get(_)).getOrElse(Paths.get("config","model-api.local.json"))
      modelClient <- ChatCompletionClient.configured(modelPath)
      _ <- LocalHttpServer.resource(url,8080,modelClient).use(_ =>
        IO.println(s"[HTTP ready] http://127.0.0.1:8080/api/ (local demo only); database: $database.mv.db") *> waitForStop).guarantee(IO.blocking(Files.deleteIfExists(stopFile)).void)
    yield ()
