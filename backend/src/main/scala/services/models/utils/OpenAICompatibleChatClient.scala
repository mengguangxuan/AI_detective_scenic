package services.models.utils

import cats.effect.IO
import io.circe.{Json, parser}
import java.net.URI
import java.net.http.{HttpClient, HttpRequest, HttpResponse}
import java.nio.charset.StandardCharsets
import java.time.Duration
import scala.concurrent.duration.*
import scala.util.Using
import services.models.api.ChatCompletionClient
import services.models.objects.{ChatCompletionInput, ModelApiSettings}

private[models] final class OpenAICompatibleChatClient(settings: ModelApiSettings) extends ChatCompletionClient:
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
    .followRedirects(HttpClient.Redirect.NEVER).build()
  override def complete(input: ChatCompletionInput): IO[String] =
    val basic = Json.obj("model" -> Json.fromString(settings.model), "stream" -> Json.False,
      "max_tokens" -> Json.fromInt(settings.maxTokens), "response_format" -> Json.obj("type" -> Json.fromString("json_object")),
      "messages" -> Json.arr(Json.obj("role" -> Json.fromString("system"), "content" -> Json.fromString(input.system)),
        Json.obj("role" -> Json.fromString("user"), "content" -> Json.fromString(input.user))))
    val body = if settings.disableThinking then basic.deepMerge(Json.obj("thinking" -> Json.obj("type" -> Json.fromString("disabled")))) else basic
    IO.interruptibleMany {
      val request = HttpRequest.newBuilder(URI.create(settings.baseUrl.stripSuffix("/") + "/chat/completions"))
        .timeout(Duration.ofSeconds(settings.timeoutSeconds)).header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + settings.apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(body.noSpaces, StandardCharsets.UTF_8)).build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
      Using.resource(response.body()) { stream =>
        val bytes = stream.readNBytes(65537)
        if response.statusCode() != 200 then System.err.println(s"[model] Provider HTTP status: ${response.statusCode()}")
        require(response.statusCode() == 200 && bytes.length <= 65536, "Model transport failed")
        val json = parser.parse(new String(bytes, StandardCharsets.UTF_8)).fold(_ => throw new IllegalArgumentException("Invalid model response"), identity)
        val choice = json.hcursor.downField("choices").downArray
        require(choice.get[String]("finish_reason").contains("stop"), "Incomplete model response")
        choice.downField("message").get[String]("content").toOption.filter(s => s.trim.nonEmpty && s.length <= 12000)
          .getOrElse(throw new IllegalArgumentException("Empty model response"))
      }
    }.timeout(settings.timeoutSeconds.seconds)
      // Never surface a provider response, config parse failure, or Authorization header.
      .handleErrorWith(error => IO.println(s"[model] Request unavailable (${error.getClass.getSimpleName}).") *>
        IO.raiseError(new IllegalStateException("Model request unavailable")))
