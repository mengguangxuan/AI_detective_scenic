package services.models
import cats.effect.IO
import cats.effect.unsafe.implicits.global
import io.circe.Json
import io.circe.syntax.*
import java.net.URI
import java.net.http.{HttpClient,HttpRequest,HttpResponse}
import munit.FunSuite
import services.models.api.{ChatCompletionClient,SceneModelBridge}
import services.models.objects.ChatCompletionInput
class SceneModelBridgeSuite extends FunSuite:
  test("private scene transport rejects untrusted requests and calls only the central model client") {
    var calls=Vector.empty[ChatCompletionInput]
    val model=new ChatCompletionClient:
      def complete(input: ChatCompletionInput): IO[String]=IO { calls=calls :+ input;"{}" }
    SceneModelBridge.resource(model).use { access =>
      val port=access.hcursor.get[Int]("port").toOption.get
      val token=access.hcursor.get[String]("token").toOption.get
      val input=ChatCompletionInput("generic prompt","unmodified raw source")
      def send(secret: String)=IO.blocking {
        val request=HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/complete")).header("X-Scene-Transport",secret).POST(HttpRequest.BodyPublishers.ofString(Json.obj("input"->input.asJson,"maxTokens"->Json.fromInt(5000)).noSpaces)).build()
        HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString())
      }
      for
        denied <- send("invalid")
        _ = assertEquals(denied.statusCode(),503)
        _ = assertEquals(calls.size,0)
        result <- send(token)
        _ = assertEquals(result.statusCode(),200)
        _ = assertEquals(calls,Vector(input))
        _ = assert(!result.body().contains(token))
      yield ()
    }.unsafeRunSync()
  }

  test("scene transport forwards explicit truncation diagnostics without requesting another completion") {
    import services.models.objects.{ModelRequestFailure,ModelFailureCode}
    var calls=0
    val model=new ChatCompletionClient:
      def complete(input: ChatCompletionInput): IO[String]=IO { calls+=1 } *> IO.raiseError(ModelRequestFailure(ModelFailureCode.IncompleteOutput,Some(200),Some("length"),Some(100),Some(20),17,Some(4500),Some("partial module code")))
    SceneModelBridge.resource(model).use { access => IO.blocking {
      val port=access.hcursor.get[Int]("port").toOption.get
      val token=access.hcursor.get[String]("token").toOption.get
      val request=HttpRequest.newBuilder(URI.create(s"http://127.0.0.1:$port/complete")).header("X-Scene-Transport",token).POST(HttpRequest.BodyPublishers.ofString(Json.obj("input"->ChatCompletionInput("generic","raw").asJson,"maxTokens"->Json.fromInt(4500)).noSpaces)).build()
      val result=HttpClient.newHttpClient().send(request,HttpResponse.BodyHandlers.ofString())
      val json=io.circe.parser.parse(result.body()).toOption.get
      assertEquals(result.statusCode(),502)
      assertEquals(json.hcursor.downField("error").get[String]("code"),Right("IncompleteOutput"))
      assertEquals(json.hcursor.downField("error").get[String]("finishReason"),Right("length"))
      assertEquals(json.hcursor.downField("error").get[String]("partialContent"),Right("partial module code"))
      assertEquals(calls,1)
      assert(!result.body().contains(token))
    }}.unsafeRunSync()
  }
