package services.system.api
import cats.effect.{IO,IOApp}
import cats.effect.unsafe.IORuntime
import java.nio.file.{Files,Paths}
import scala.concurrent.duration.*
import services.accounts.tables.LocalIdentityTable.LocalIdentityTableInitializer
import services.cases.api.ImportCaseMessage
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
        _ <- DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c))
      yield () }
      _ <- LocalHttpServer.resource(url,8080).use(_ =>
        IO.println(s"[HTTP ready] http://127.0.0.1:8080/api/ (local demo only); database: $database.mv.db") *> waitForStop).guarantee(IO.blocking(Files.deleteIfExists(stopFile)).void)
    yield ()
