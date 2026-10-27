package services.system.api

import cats.effect.{IO,IOApp}
import cats.syntax.all.*
import java.nio.file.{Files,Paths,StandardCopyOption}
import java.time.Instant
import services.games.api.MigrateLegacyMessage

// Offline tool: backup old files, read them without write access, populate db-v2 transactionally.
object MigrateLegacyDemo extends IOApp.Simple:
  override def run: IO[Unit] =
    val platform=if System.getProperty("os.name").toLowerCase(java.util.Locale.ROOT).contains("windows") then "windows" else "linux"
    val base=Paths.get("target","fixture-demo").toAbsolutePath.normalize()
    val target=base.resolve(platform).resolve("db-v2")
    val sources=Vector(base.resolve("db"),base.resolve(platform).resolve("db"))
    val fixture=Paths.get("../docs/fixtures/minimal_investigation.json")
    for
      _ <- IO.blocking(Files.createDirectories(target.getParent))
      _ <- sources.traverse_ { source =>
        val file=Paths.get(s"${source}.mv.db")
        IO.blocking(Files.exists(file)).flatMap { exists =>
          if !exists then IO.unit
          else
            for
              backup <- IO.blocking {
                val dest=file.resolveSibling(file.getFileName.toString+s".before-v2-${Instant.now().toEpochMilli}.bak")
                Files.copy(file,dest,StandardCopyOption.COPY_ATTRIBUTES)
                dest
              }
              _ <- IO.println(s"旧库备份：$backup")
              report <- DatabaseSession.connection(s"jdbc:h2:file:$target;LOCK_TIMEOUT=10000").use { c =>
                DatabaseSchema.initialize(c) *> DatabaseSession.transaction(c)(
                  MigrateLegacyMessage(s"jdbc:h2:file:$source;IFEXISTS=TRUE;ACCESS_MODE_DATA=r",fixture).plan(c))
              }
              _ <- IO.println(s"[迁移通过] $source：核对会话 ${report.sessions}，调查记录 ${report.records}，回执 ${report.receipts}")
            yield ()
        }
      }
      _ <- IO.println(s"目标库：${target}.mv.db；旧库未修改。")
    yield ()
