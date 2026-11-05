package services.sceneproduction.api
import cats.effect.{IO,IOApp,ExitCode}
import io.circe.{Json,parser}
import io.circe.syntax.*
import java.nio.file.{Files,Path,Paths,StandardCopyOption}
import services.models.api.ChatCompletionClient
import services.models.objects.{ChatCompletionInput,ModelRequestFailure}

// Explicit operator diagnosis: repeat one saved text request once, never generate a 3D asset
// or execute its answer. Preserve the original failed task and account for this call.
object DiagnoseSceneRequest extends IOApp:
  private def read(file: Path): Json=parser.parse(Files.readString(file)).fold(throw _,identity)
  private def write(file: Path,value: Json): Unit=
    val temp=Files.createTempFile(file.getParent,"diagnostic-",".tmp")
    Files.writeString(temp,value.spaces2)
    Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING,StandardCopyOption.ATOMIC_MOVE)
  private[sceneproduction] def diagnose(folder: Path,requestFile: Path,client: ChatCompletionClient): IO[Unit] =
    for
      prepared <- IO.blocking {
        val status=read(folder.resolve("status.json"))
        require(status.hcursor.get[String]("stage").contains("failed"),"Diagnosis requires a stopped failed task")
        val calls=status.hcursor.get[Int]("calls").toOption.get
        require(calls>=0 && calls<88,"Model call budget exhausted")
        val request=read(requestFile)
        val messages=request.hcursor.get[Vector[Json]]("messages").toOption.get
        require(messages.size==2 && messages(0).hcursor.get[String]("role").contains("system") && messages(1).hcursor.get[String]("role").contains("user"))
        val input=ChatCompletionInput(messages(0).hcursor.get[String]("content").toOption.get,messages(1).hcursor.get[String]("content").toOption.get)
        val max=request.hcursor.get[Int]("max_tokens").toOption.get
        require(max>0 && max<=6000)
        // Reserve before contacting the provider. An ambiguous failure never silently repeats.
        Files.createFile(folder.resolve(requestFile.getFileName.toString+".diagnostic-once"))
        val prefix=folder.resolve("requests").resolve(f"${calls+1}%02d-diagnostic")
        write(Path.of(prefix.toString+".request.json"),request.mapObject(_.add("diagnosticOf",Json.fromString(requestFile.getFileName.toString))))
        write(folder.resolve("status.json"),status.mapObject(_.add("calls",Json.fromInt(calls+1))))
        (input,max,prefix)
      }
      result <- client.completeScene(prepared._1,prepared._2).attempt
      _ <- IO.blocking {
        val status=read(folder.resolve("status.json"))
        val (diagnostic,tokens)=result match
          case Right(response) =>
            write(Path.of(prepared._3.toString+".response.json"),response.asJson)
            (Json.obj("outcome"->Json.fromString("completed"),"source"->Json.fromString(prepared._3.getFileName.toString)),response.totalTokens)
          case Left(failure: ModelRequestFailure) =>
            write(Path.of(prepared._3.toString+".failure.json"),failure.asJson)
            (failure.asJson.mapObject(_.remove("partialContent")),failure.totalTokens)
          case Left(_) =>
            val error=Json.obj("outcome"->Json.fromString("unavailable"))
            write(Path.of(prepared._3.toString+".failure.json"),error)
            (error,0L)
        val total=status.hcursor.get[Long]("tokens").getOrElse(0L)+tokens
        write(folder.resolve("status.json"),status.mapObject(_.add("tokens",Json.fromLong(total)).add("lastDiagnostic",diagnostic)))
        val safe=diagnostic.mapObject(_.remove("source"))
        println(s"[scene diagnostic] ${safe.noSpaces}; original pipeline remains stopped; no answer executed.")
      }
    yield ()
  def run(args: List[String]): IO[ExitCode] =
    require(args.size==2 && args.head.matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}") && args(1).matches("[0-9]{1,2}"),"Expected production ID and request number")
    val project=Paths.get("..").toAbsolutePath.normalize()
    val folder=project.resolve("backend/.build-cache/scene-production").resolve(args.head)
    for
      pair <- IO.blocking {
        val config=Option(System.getenv("DETECTIVE_MODEL_CONFIG")).filter(_.nonEmpty).map(Paths.get(_)).getOrElse(Paths.get("config/model-api.local.json"))
        require(read(config).hcursor.get[String]("model").exists(_.startsWith("deepseek-")),"Diagnosis supports the configured DeepSeek text model only")
        val paths=Files.list(folder.resolve("requests"))
        val candidates=try paths.filter(p=>p.getFileName.toString.startsWith(f"${args(1).toInt}%02d-") && p.getFileName.toString.endsWith(".request.json")).toList
          finally paths.close()
        require(candidates.size()==1,"Expected one saved request")
        (config,candidates.get(0))
      }
      client <- ChatCompletionClient.configured(pair._1)
      _ <- diagnose(folder,pair._2,client)
    yield ExitCode.Success
