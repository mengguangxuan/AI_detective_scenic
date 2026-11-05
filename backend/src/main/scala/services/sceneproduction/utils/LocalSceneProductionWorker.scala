package services.sceneproduction.utils
import cats.effect.IO
import io.circe.{Json,parser}
import io.circe.syntax.*
import java.nio.file.{Files,Path,StandardCopyOption,LinkOption,FileAlreadyExistsException}
import scala.jdk.CollectionConverters.*
import scala.jdk.OptionConverters.*
import scala.concurrent.duration.*
import services.sceneproduction.api.SceneProductionWorker
import services.sceneproduction.objects.*
import services.system.objects.*

// External model work never owns a JDBC connection. Artifacts remain author-private.
private[sceneproduction] final class LocalSceneProductionWorker(projectRoot: Path,transport: Option[Json] = None) extends SceneProductionWorker:
  private val project=projectRoot.toAbsolutePath.normalize()
  private val jobs=project.resolve("backend/.build-cache/scene-production")
  private val saved=project.resolve("backend/target/scene-artifacts")
  private val launchedProcesses=new java.util.concurrent.ConcurrentHashMap[String,Process]()
  private val emptyProgress=SceneProductionProgress(0,0,0,0,0)
  private def path(base: Path,id: SceneProductionId): Path =
    require(id.value.matches("[0-9a-f-]{36}"));base.resolve(id.value)
  private def source(row: SceneProductionRecord): Path = path(if row.phase==SceneProductionPhase.Saved then saved else jobs,row.id)
  private def read(file: Path): Option[Json] =
    if !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) || Files.size(file)>2000000 then None
    else parser.parse(Files.readString(file)).toOption
  private def accepted(dir: Path): Boolean =
    read(dir.resolve("status.json")).exists { json =>
      val c=json.hcursor
      c.get[String]("stage").contains("passed") && c.downField("reviews").downField("visual").get[Boolean]("passed").contains(true) &&
        c.downField("reviews").downField("overall").get[Boolean]("passed").contains(true)
    } && read(dir.resolve("program-checks.json")).exists(_.hcursor.get[Boolean]("passed").contains(true))
  private def alive(pid: Long,id: String): Boolean =
    val process=if pid>0 then ProcessHandle.of(pid) else java.util.Optional.empty[ProcessHandle]()
    val owned=Option(launchedProcesses.get(id)).exists(p=>p.pid()==pid && p.isAlive)
    owned || (process.isPresent && process.get().isAlive && {
      val info=process.get().info()
      Vector(jobs,saved).exists { base =>
        val file=path(base,SceneProductionId(id)).resolve("launch.json")
        read(file).filter(_.hcursor.get[Long]("pid").contains(pid)).exists { record =>
          val start=record.hcursor.get[String]("startedAt").toOption.flatMap(s=>scala.util.Try(java.time.Instant.parse(s)).toOption)
          SceneProcessIdentity.matches(start,info.startInstant().toScala,Files.getLastModifiedTime(file).toInstant,info.commandLine().toScala,id)
        }
      }
    })
  private def launch(dir: Path,viewOnly: Boolean,resuming: Boolean=false): Unit =
    val node=Option(System.getenv("DETECTIVE_SCENE_NODE")).filter(_.nonEmpty).getOrElse(if System.getProperty("os.name").toLowerCase.contains("windows") then "E:\\dev\\nodejs\\node.exe" else "node")
    val args=Vector(node,project.resolve("tools/run_scene_pipeline.mjs").toString,"--production",dir.toString) ++ (if viewOnly then Vector("--view-only") else Vector.empty) ++ (if resuming then Vector("--resume") else Vector.empty)
    val builder=new ProcessBuilder(args.asJava).directory(project.toFile)
      .redirectOutput(ProcessBuilder.Redirect.appendTo(dir.resolve("worker.out.log").toFile))
      .redirectError(ProcessBuilder.Redirect.appendTo(dir.resolve("worker.err.log").toFile))
    if System.getProperty("os.name").toLowerCase.contains("windows") then builder.environment().put("PLAYWRIGHT_BROWSERS_PATH","E:\\dev\\playwright-browsers")
    transport.foreach(value=>Files.writeString(dir.resolve("transport.json"),value.noSpaces))
    val process=builder.start()
    launchedProcesses.put(dir.getFileName.toString,process)
    val startedAt=process.info().startInstant().toScala.map(t=>Json.fromString(t.toString)).getOrElse(Json.Null)
    Files.writeString(dir.resolve("launch.json"),Json.obj("pid" -> Json.fromLong(process.pid()),"startedAt" -> startedAt).noSpaces)
  override def start(row: SceneProductionRecord): IO[Unit] = IO.blocking {
    if row.phase==SceneProductionPhase.Queued then
      val dir=path(jobs,row.id);Files.createDirectories(dir)
      val reserve=dir.resolve("launch-once")
      val fresh=try { Files.createFile(reserve);true } catch case _: FileAlreadyExistsException => false
      if fresh then
        try
          Files.writeString(dir.resolve("author-input.json"),row.input.asJson.noSpaces)
          launch(dir,false)
        catch case _: Exception => Files.writeString(dir.resolve("launch-failed"),"Scene worker launch unavailable")
  }
  override def resume(row: SceneProductionRecord,expectedCalls: Int): IO[Unit] = IO.blocking { synchronized {
    val dir=source(row)
    if row.phase!=SceneProductionPhase.Queued || expectedCalls<0 || expectedCalls>=88 then throw GameError(ErrorCode.ConditionFailed,"当前任务不能继续制作。")
    val marker=dir.resolve(s"resume-$expectedCalls")
    if !Files.exists(marker) then
      val status=read(dir.resolve("status.json")).getOrElse(throw GameError(ErrorCode.ConditionFailed,"任务记录不完整，不能继续。"))
      val c=status.hcursor
      if !c.get[Int]("calls").contains(expectedCalls) then throw GameError(ErrorCode.StaleState,"制作进度已变化，请刷新后继续。")
      if !c.get[String]("stage").exists(Set("failed","needs_attention")) || c.get[Int]("round").getOrElse(0)>=10 then throw GameError(ErrorCode.ConditionFailed,"任务正在运行或已达检查上限，请先查看现有进度。")
      val fresh=try { Files.createFile(marker);true } catch case _: FileAlreadyExistsException => false
      if fresh then
        val pid=read(dir.resolve("launch.json")).flatMap(_.hcursor.get[Long]("pid").toOption)
        pid.filter(p=>alive(p,row.id.value)).foreach { id =>
          val process=ProcessHandle.of(id)
          if process.isPresent then
            process.get().destroy()
            process.get().onExit().get(5,java.util.concurrent.TimeUnit.SECONDS)
        }
        Files.writeString(dir.resolve("status.json"),status.mapObject(_.add("stage",Json.fromString("created")).add("stageLabel",Json.fromString("正在恢复制作")).add("message",Json.fromString("沿用原始资料与已有模型返回，继续未完成步骤。"))).noSpaces)
        try launch(dir,false,true)
        catch case _: Exception =>
          Files.writeString(dir.resolve("status.json"),status.mapObject(_.add("stageLabel",Json.fromString("制作恢复未启动")).add("message",Json.fromString("资料已保留；恢复进程未能启动，未自动重发。"))).noSpaces)
  }}
  private def layout(dir: Path): Option[SceneLayoutView] = read(dir.resolve("plan.json")).map { plan =>
    val c=plan.hcursor
    val modules=c.get[Vector[Json]]("modules").getOrElse(Vector.empty)
    val items=c.get[Vector[Json]]("instances").getOrElse(Vector.empty).flatMap { instance =>
      val i=instance.hcursor
      for
        id <- i.get[String]("id").toOption
        module <- i.get[String]("module").toOption
        index <- i.get[Int]("specIndex").toOption
        definition <- modules.find(_.hcursor.get[String]("id").contains(module))
        specs <- definition.hcursor.get[Vector[Json]]("specs").toOption
        spec <- specs.lift(index)
        x <- i.downField("placement").downField("positionMeters").get[Double]("x").toOption.filter(_.isFinite)
        z <- i.downField("placement").downField("positionMeters").get[Double]("z").toOption.filter(_.isFinite)
        w <- spec.hcursor.downField("sizeBudget").get[Double]("widthMeters").toOption.filter(v=>v.isFinite&&v>0)
        d <- spec.hcursor.downField("sizeBudget").get[Double]("depthMeters").toOption.filter(v=>v.isFinite&&v>0)
        sx <- i.downField("placement").downField("scale").get[Double]("x").toOption.filter(v=>v.isFinite&&v>0)
        sz <- i.downField("placement").downField("scale").get[Double]("z").toOption.filter(v=>v.isFinite&&v>0)
        rotation <- i.downField("placement").downField("rotation").get[Double]("yRad").toOption.filter(_.isFinite)
      yield SceneLayoutItem(id,definition.hcursor.get[String]("task").getOrElse(module).take(100),x,z,w*sx,d*sz,rotation)
    }
    SceneLayoutView(c.downField("design").get[String]("title").getOrElse("模型布局"),c.downField("design").get[Vector[Json]]("decisions").getOrElse(Vector.empty).flatMap(_.hcursor.get[String]("description").toOption),items)
  }
  override def confirmLayout(row: SceneProductionRecord): IO[Unit] =
    val dir=source(row)
    def access: IO[Option[Json]] = IO.blocking(read(dir.resolve("access.json")).filter(j=>j.hcursor.get[Long]("pid").exists(p=>alive(p,row.id.value))))
    def awaitAccess(attempts: Int): IO[Json] = access.flatMap {
      case Some(value) => IO.pure(value)
      case None if attempts>0 => IO.sleep(250.millis) *> awaitAccess(attempts-1)
      case None => IO.raiseError(GameError(ErrorCode.RequestPending,"布局服务正在准备，请稍后重试确认。"))
    }
    for
      current <- status(row)
      _ <- if Files.exists(dir.resolve("layout-approved.json")) then IO.unit
        else if current.stage!="awaiting_layout" then IO.raiseError(GameError(ErrorCode.ConditionFailed,"当前没有等待确认的布局。"))
        else for
          _ <- IO.blocking { synchronized {
            transport.foreach(value=>Files.writeString(dir.resolve("transport.json"),value.noSpaces))
            val active=read(dir.resolve("access.json")).exists(j=>j.hcursor.get[Long]("pid").exists(p=>alive(p,row.id.value)))
            if !active then launch(dir,true)
          }}
          ready <- awaitAccess(20)
          _ <- IO.blocking {
            val port=ready.hcursor.get[Int]("port").toOption.filter(p=>p>0&&p<65536).getOrElse(throw GameError(ErrorCode.ServerFailure,"布局服务地址无效。"))
            val token=ready.hcursor.get[String]("token").toOption.filter(_.matches("[0-9a-f]{64}")).getOrElse(throw GameError(ErrorCode.ServerFailure,"布局服务凭证无效。"))
            val request=java.net.http.HttpRequest.newBuilder(java.net.URI.create(s"http://127.0.0.1:$port/control/confirm-layout")).timeout(java.time.Duration.ofSeconds(5)).header("X-Scene-Control",token).POST(java.net.http.HttpRequest.BodyPublishers.noBody()).build()
            val response=java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NEVER).build().send(request,java.net.http.HttpResponse.BodyHandlers.discarding())
            if response.statusCode()!=200&&response.statusCode()!=202 then throw GameError(ErrorCode.ConditionFailed,"布局确认未完成，请重新读取任务后重试。")
          }
        yield ()
    yield ()
  override def status(row: SceneProductionRecord): IO[SceneProductionStatus] = IO.blocking {
    val dir=source(row)
    if row.phase==SceneProductionPhase.Draft then SceneProductionStatus("draft","草稿","原始资料已保存，可以继续编辑。",0,10,0,0,emptyProgress,Vector.empty)
    else read(dir.resolve("status.json")) match
      case Some(json) =>
        val c=json.hcursor
        val stages=Set("created","awaiting_layout","planning","generating","assembling","capturing","visual_review","overall_review","repairing","diagnosing","passed","needs_attention","needs_input","failed")
        val raw=c.get[String]("stage").getOrElse("failed")
        val current=if stages(raw) then raw else "failed"
        val validPassed=current!="passed" || accepted(dir)
        val running=Set("created","planning","generating","assembling","capturing","visual_review","overall_review","repairing","diagnosing")
        val pid=read(dir.resolve("launch.json")).flatMap(_.hcursor.get[Long]("pid").toOption).getOrElse(0L)
        val interrupted=running(current) && pid>0 && !alive(pid,row.id.value)
        val stage=if !validPassed || interrupted then "failed" else current
        val issues=c.get[Vector[Json]]("issues").getOrElse(Vector.empty).take(40).map { issue =>
          val i=issue.hcursor
          SceneProductionIssue(Vector("evidence","message","error","question").flatMap(k=>i.get[String](k).toOption).mkString(" ").take(2000),i.get[String]("severity").getOrElse("blocking"))
        }
        SceneProductionStatus(stage,c.get[String]("stageLabel").getOrElse("制作任务").take(100),if interrupted then "后台制作已中断，资料与已完成检查已保留；可复制为新草稿后重试。" else if validPassed then c.get[String]("message").getOrElse("").take(1000) else "验收记录不完整，尚不能保存场景。",c.get[Int]("round").getOrElse(0),c.downField("limits").get[Int]("maxRounds").getOrElse(10),c.get[Int]("calls").getOrElse(0),c.get[Long]("tokens").getOrElse(0),c.downField("progress").as[SceneProductionProgress].getOrElse(emptyProgress),issues,layout(dir))
      case None =>
        val pid=read(dir.resolve("launch.json")).flatMap(_.hcursor.get[Long]("pid").toOption).getOrElse(0L)
        val failed=Files.exists(dir.resolve("launch-failed")) || (pid>0 && !alive(pid,row.id.value))
        SceneProductionStatus(if failed then "failed" else "created",if failed then "制作服务未启动" else "正在启动制作",if failed then "原始资料已保留，请检查本机制作服务配置。" else "等待后台制作进程就绪。",0,10,0,0,emptyProgress,Vector.empty)
  }
  override def save(row: SceneProductionRecord): IO[Unit] = IO.blocking {
    val from=source(row)
    if !accepted(from) then throw GameError(ErrorCode.ConditionFailed,"场景尚未通过运行、视觉和整体审核，暂不能保存成品。")
    val to=path(saved,row.id)
    if !Files.exists(to) then
      Files.createDirectories(saved)
      val staging=Files.createTempDirectory(saved,"scene-save-")
      Files.createDirectories(staging.resolve("modules"))
      Vector("input.json","status.json","plan.json","program-checks.json","assembler.js").foreach { name =>
        val file=from.resolve(name)
        if !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) then throw GameError(ErrorCode.ConditionFailed,"场景资源不完整。")
        Files.copy(file,staging.resolve(name))
      }
      val plan=read(from.resolve("plan.json")).getOrElse(throw GameError(ErrorCode.ConditionFailed,"场景计划缺失。"))
      val ids=plan.hcursor.get[Vector[Json]]("modules").getOrElse(Vector.empty).flatMap(_.hcursor.get[String]("id").toOption)
      if ids.isEmpty || ids.exists(id=> !id.matches("[a-z][a-z0-9_]{0,45}")) then throw GameError(ErrorCode.ConditionFailed,"场景模块登记无效。")
      ids.foreach { id =>
        val file=from.resolve("modules").resolve(id+".js")
        if !Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) then throw GameError(ErrorCode.ConditionFailed,"场景模块缺失。")
        Files.copy(file,staging.resolve("modules").resolve(id+".js"))
      }
      val evidence=Files.list(from)
      try evidence.iterator().asScala.filter(p=>p.getFileName.toString.matches("(view-[0-9]+\\.png|(?:visual|overall)-review-[0-9]+\\.json|layout-approved\\.json)") && Files.isRegularFile(p,LinkOption.NOFOLLOW_LINKS)).foreach(p=>Files.copy(p,staging.resolve(p.getFileName)))
      finally evidence.close()
      try Files.move(staging,to,StandardCopyOption.ATOMIC_MOVE)
      catch case _: FileAlreadyExistsException => ()
    if !accepted(to) then throw GameError(ErrorCode.ConditionFailed,"保存的验收记录不完整。")
  }
  override def preview(row: SceneProductionRecord): IO[Option[String]] = IO.blocking { synchronized {
    val dir=source(row)
    if !accepted(dir) then None
    else
      def access: Option[String] = read(dir.resolve("access.json")).flatMap { json =>
        val c=json.hcursor
        for
          port <- c.get[Int]("port").toOption.filter(p=>p>0&&p<65536)
          pid <- c.get[Long]("pid").toOption.filter(p=>alive(p,row.id.value))
          token <- c.get[String]("token").toOption.filter(_.matches("[0-9a-f]{64}"))
        yield s"http://127.0.0.1:$port/author-preview?token=$token"
      }
      val existing=access
      if existing.isDefined then existing
      else
        val launching=read(dir.resolve("launch.json")).flatMap(_.hcursor.get[Long]("pid").toOption).exists(p=>alive(p,row.id.value))
        if !launching then launch(dir,true)
        None
  }}
