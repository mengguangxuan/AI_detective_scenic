package services.system.api
import cats.effect.IO
import cats.effect.unsafe.IORuntime
import com.sun.net.httpserver.{HttpExchange,HttpHandler}
import io.circe.{Json,parser}
import io.circe.syntax.*
import java.nio.charset.StandardCharsets
import services.system.objects.*
import services.system.objects.apiTypes.HttpReply

// Loopback development transport. No CORS, no client-supplied user identity, no file/import endpoints.
object APIMessageRouter:
  def handler(databaseUrl: String, registrations: Vector[RegisteredAPIMessage], port: Int)(using IORuntime): HttpHandler =
    require(registrations.map(_.name).distinct.size == registrations.size)
    val routes = registrations.map(r => r.name -> r).toMap
    new HttpHandler:
      override def handle(exchange: HttpExchange): Unit =
        val action = for
          route <- IO.blocking {
            val host = Option(exchange.getRequestHeaders.getFirst("Host")).getOrElse("")
            val allowedHosts = Set(s"127.0.0.1:$port",s"localhost:$port","127.0.0.1:5173","localhost:5173","127.0.0.1:5175","localhost:5175")
            val origin = Option(exchange.getRequestHeaders.getFirst("Origin"))
            if !allowedHosts(host) || origin.exists(_ != s"http://$host") then
              throw GameError(ErrorCode.AccessDenied,"仅允许同源本地演示请求。")
            if exchange.getRequestMethod != "POST" || exchange.getRequestHeaders.getFirst("X-Detective-Client") != "web" ||
                !Option(exchange.getRequestHeaders.getFirst("Content-Type")).exists(_.split(';').head.trim.equalsIgnoreCase("application/json")) then
              throw GameError(ErrorCode.InvalidValue,"请使用 JSON 消息请求。")
            val path = exchange.getRequestURI.getPath
            routes.getOrElse(path.stripPrefix("/api/"),throw GameError(ErrorCode.InvalidValue,"未注册的 API。"))
          }
          payload <- IO.blocking {
            val bytes = exchange.getRequestBody.readNBytes(8193)
            if bytes.length > 8192 then throw GameError(ErrorCode.InvalidValue,"请求内容过长。")
            parser.parse(new String(bytes,StandardCharsets.UTF_8)).fold(_ => throw GameError(ErrorCode.InvalidValue,"请求不是合法 JSON。"),identity)
          }
          _ <- IO.blocking(validate(payload,route.fields))
          token = Option(exchange.getRequestHeaders.getFirst("Cookie")).toVector.flatMap(_.split(';')).map(_.trim)
            .find(_.startsWith("detective_local=")).map(_.stripPrefix("detective_local="))
          reply <- DatabaseSession.connection(databaseUrl).use(c => DatabaseSession.transaction(c)(route.run(payload,token,c)))
          _ <- IO.blocking(send(exchange,200,reply))
        yield ()
        action.handleErrorWith { error =>
          val publicError = error match
            case known: GameError => known
            case _ => GameError(ErrorCode.ServerFailure,"服务暂时无法完成请求，请保留原请求重试。")
          val status = publicError.code match
            case ErrorCode.Unauthenticated => 401
            case ErrorCode.AccessDenied => 403
            case ErrorCode.StaleState | ErrorCode.RequestConflict | ErrorCode.ConflictingContent | ErrorCode.ConditionFailed => 409
            case ErrorCode.ServerFailure => 500
            case _ => 400
          (if status == 500 then IO.println(s"[HTTP failure] ${error.getClass.getSimpleName}") else IO.unit) *>
            IO.blocking(send(exchange,status,HttpReply(publicError.asJson)))
        }.guarantee(IO.blocking(exchange.close())).unsafeRunSync()

  private def validate(payload: Json,fields: Set[String]): Unit =
    val obj = payload.asObject.getOrElse(throw GameError(ErrorCode.InvalidValue,"请求必须是 JSON 对象。"))
    if obj.keys.toSet != fields then throw GameError(ErrorCode.InvalidValue,"请求字段缺失或包含不允许的字段。")
    if obj.values.exists(_.asString.exists(_.length > 200)) then throw GameError(ErrorCode.InvalidValue,"请求编号过长。")
    obj("expectedSequence").foreach { json =>
      val valid = json.asObject.exists(o => o.keys.toSet == Set("value")) &&
        json.hcursor.get[Long]("value").exists(n => n >= 0 && n <= 9007199254740991L)
      if !valid then throw GameError(ErrorCode.InvalidValue,"请求序号不合法。")
    }

  private def send(exchange: HttpExchange,status: Int,reply: HttpReply): Unit =
    val bytes = reply.body.noSpaces.getBytes(StandardCharsets.UTF_8)
    exchange.getResponseHeaders.set("Content-Type","application/json; charset=utf-8")
    exchange.getResponseHeaders.set("Cache-Control","no-store")
    exchange.getResponseHeaders.set("X-Content-Type-Options","nosniff")
    reply.cookie.foreach(token => exchange.getResponseHeaders.set("Set-Cookie",s"detective_local=$token; HttpOnly; SameSite=Strict; Path=/api; Max-Age=31536000"))
    exchange.sendResponseHeaders(status,bytes.length)
    exchange.getResponseBody.write(bytes)
