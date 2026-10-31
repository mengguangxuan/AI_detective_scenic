package services.system

import cats.effect.{IO,Resource}
import cats.effect.unsafe.implicits.global
import com.sun.net.httpserver.HttpServer
import io.circe.{Json,parser}
import java.net.{InetSocketAddress,URI}
import java.net.http.{HttpClient,HttpRequest,HttpResponse}
import java.nio.charset.StandardCharsets
import java.nio.file.{Files,Paths}
import java.util.UUID
import java.util.concurrent.{ConcurrentLinkedQueue,Executors}
import munit.FunSuite
import services.models.api.ChatCompletionClient
import services.models.objects.{ChatCompletionInput,ModelApiSettings}
import services.system.api.*
import services.accounts.tables.LocalIdentityTable.LocalIdentityTableInitializer
import services.cases.api.ImportCaseMessage

class ModelHttpSuite extends FunSuite:
  private def provider(status: Int,body: String,requests: ConcurrentLinkedQueue[(Boolean,Json)],delayMillis: Long=0): Resource[IO,Int] =
    Resource.make(IO.blocking {
      val server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),8)
      val executor=Executors.newFixedThreadPool(2)
      server.setExecutor(executor)
      server.createContext("/v1/chat/completions",exchange => {
        val raw=new String(exchange.getRequestBody.readAllBytes(),StandardCharsets.UTF_8)
        requests.add((exchange.getRequestHeaders.getFirst("Authorization")=="Bearer dummy-test-key",parser.parse(raw).toOption.get))
        try
          if delayMillis>0 then Thread.sleep(delayMillis)
          val bytes=body.getBytes(StandardCharsets.UTF_8)
          exchange.getResponseHeaders.set("Content-Type","application/json")
          exchange.sendResponseHeaders(status,bytes.length)
          exchange.getResponseBody.write(bytes)
        catch case _: Exception => ()
        finally exchange.close()
      })
      server.start()
      (server,executor)
    }) { case (server,executor) => IO.blocking { server.stop(0); executor.shutdownNow(); () } }.map(_._1.getAddress.getPort)
  private def envelope(answer: String,finish: String="stop"): String = Json.obj("choices" -> Json.arr(Json.obj(
    "finish_reason" -> Json.fromString(finish),"message" -> Json.obj("content" -> Json.fromString(answer))))).noSpaces
  private def settings(port: Int) = ModelApiSettings(s"http://127.0.0.1:$port/v1","dummy-test-key","configured-test-model",2,512,true)

  test("independent provider client sends the DeepSeek-compatible JSON contract") {
    val requests=new ConcurrentLinkedQueue[(Boolean,Json)]()
    provider(200,envelope("""{"reply":"那座钟我一直拨快一刻钟。","disclosureIds":["clement_habit"]}"""),requests).use { port => for
      client <- ChatCompletionClient.fromSettings(settings(port))
      response <- client.complete(ChatCompletionInput("Select an answer as json","clock?"))
      _ = assert(response.contains("clement_habit"))
      _ = assertEquals(requests.size(),1)
      sent=requests.peek()
      _ = assert(sent._1)
      _ = assertEquals(sent._2.hcursor.get[String]("model"),Right("configured-test-model"))
      _ = assertEquals(sent._2.hcursor.get[Boolean]("stream"),Right(false))
      _ = assertEquals(sent._2.hcursor.downField("response_format").get[String]("type"),Right("json_object"))
      _ = assertEquals(sent._2.hcursor.downField("thinking").get[String]("type"),Right("disabled"))
      _ = assertEquals(sent._2.hcursor.get[Vector[Json]]("messages").toOption.get.size,2)
    yield () }.unsafeRunSync()
  }

  test("student TokenHub profile uses one v1 prefix, Bearer auth and the assigned text model") {
    val requests=new ConcurrentLinkedQueue[(Boolean,Json)]()
    provider(200,envelope("""{"reply":"我按现有材料回答。","disclosureIds":[]}"""),requests).use { port => for
      client <- ChatCompletionClient.fromSettings(settings(port).copy(baseUrl=s"http://127.0.0.1:$port/v1/",model="deepseek-v4.1-flash",maxTokens=1024))
      response <- client.complete(ChatCompletionInput("请严格输出JSON，不补造案情。","这份材料说明什么？"))
      _ = assert(response.contains("disclosureIds"))
      _ = assertEquals(requests.size(),1)
      sent=requests.peek()
      _ = assert(sent._1)
      _ = assertEquals(sent._2.hcursor.get[String]("model"),Right("deepseek-v4.1-flash"))
      _ = assertEquals(sent._2.hcursor.get[Int]("max_tokens"),Right(1024))
      _ = assertEquals(sent._2.hcursor.downField("response_format").get[String]("type"),Right("json_object"))
      _ = assertEquals(sent._2.hcursor.downField("thinking").get[String]("type"),Right("disabled"))
      messages=sent._2.hcursor.get[Vector[Json]]("messages").toOption.get
      _ = assertEquals(messages.map(_.hcursor.get[String]("role").toOption.get),Vector("system","user"))
      _ = assert(!sent._2.noSpaces.contains("dummy-test-key"))
    yield () }.unsafeRunSync()
  }

  test("provider errors, truncated output and timeouts fail without leaking headers or body") {
    val cases=Vector((401,"DO_NOT_LEAK_PROVIDER_BODY",0L),(200,envelope("partial","length"),0L),(200,envelope("{}"),3000L))
    cases.foreach { case (status,body,delay) =>
      provider(status,body,new ConcurrentLinkedQueue(),delay).use { port => for
        client <- ChatCompletionClient.fromSettings(settings(port))
        result <- client.complete(ChatCompletionInput("json","hello")).attempt
        _ = assert(result.isLeft)
        _ = assert(!result.left.toOption.get.getMessage.contains("DO_NOT_LEAK"))
        _ = assert(!result.left.toOption.get.getMessage.contains("dummy-test-key"))
      yield () }.unsafeRunSync()
    }
  }

  test("real HTTP question route checks credentials, long text, evidence and persisted retry after HTTP restart") {
    val requests=new ConcurrentLinkedQueue[(Boolean,Json)]()
    val directory=Files.createTempDirectory("witness-http-")
    val url=s"jdbc:h2:file:${directory.resolve("case")};LOCK_TIMEOUT=3000"
    val session=UUID.randomUUID().toString
    val http=HttpClient.newHttpClient()
    def send(port: Int,name: String,body: String,cookie: Option[String]=None): IO[(Int,Json,Option[String])] = IO.blocking {
      val builder=HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/api/$name"))
        .header("Content-Type","application/json").header("X-Detective-Client","web")
      cookie.foreach(builder.header("Cookie",_))
      val result=http.send(builder.POST(HttpRequest.BodyPublishers.ofString(body)).build(),HttpResponse.BodyHandlers.ofString())
      val setCookie=result.headers().firstValue("Set-Cookie")
      (result.statusCode(),parser.parse(result.body()).toOption.get,if setCookie.isPresent then Some(setCookie.get.split(';').head) else None)
    }
    val question="这座钟走得准吗？"+"请回忆一下。"*50
    val body=Json.obj("sessionId" -> Json.fromString(session),"requestId" -> Json.fromString("free-http"),
      "expectedSequence" -> Json.obj("value" -> Json.fromInt(0)),"witness" -> Json.fromString("clement"),
      "question" -> Json.fromString(question),"evidence" -> Json.arr()).noSpaces
    provider(200,envelope("""{"reply":"那座钟我一直拨快一刻钟。","disclosureIds":["clement_habit"]}"""),requests).use { port => for
      client <- ChatCompletionClient.fromSettings(settings(port))
      _ <- DatabaseSession.connection(url).use(c => DatabaseSchema.initialize(c) *> LocalIdentityTableInitializer.initialize(c) *>
        DatabaseSession.transaction(c)(ImportCaseMessage(Paths.get("../docs/fixtures/browser_investigation.json")).plan(c)).void)
      saved <- LocalHttpServer.resource(url,0,client).use { server =>
        val local=server.getAddress.getPort
        for
          identity <- send(local,"openlocalidentity","{}")
          _ <- send(local,"openstudy",s"""{"sessionId":"$session"}""",identity._3)
          missing <- send(local,"askwitness",body)
          forged <- send(local,"askwitness",body.replace("\"evidence\":[]","\"evidence\":[\"future_report\"]"),identity._3)
          _ = assertEquals(Vector(missing._1,forged._1),Vector(401,403))
          learned <- send(local,"askwitness",body,identity._3)
          _ = assertEquals(learned._1,200)
          snapshot <- send(local,"getinvestigationsnapshot",s"""{"sessionId":"$session"}""",identity._3)
          _ = assertEquals(snapshot._2.hcursor.get[Vector[Json]]("interviews").toOption.get.size,1)
          _ = assert(!snapshot._2.noSpaces.contains("dummy-test-key"))
        yield (identity._3,learned._2,snapshot._2)
      }
      _ <- LocalHttpServer.resource(url,0,client).use { server => for
        retried <- send(server.getAddress.getPort,"askwitness",body,saved._1)
        restored <- send(server.getAddress.getPort,"getinvestigationsnapshot",s"""{"sessionId":"$session"}""",saved._1)
        _ = assertEquals(retried._2,saved._2)
        _ = assertEquals(restored._2,saved._3)
        _ = assertEquals(requests.size(),1)
      yield () }
    yield () }.unsafeRunSync()
  }
