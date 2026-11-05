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
import services.models.objects.{ChatCompletionInput, ModelApiSettings, SceneCompletionResult, ModelRequestFailure, ModelFailureCode}

private[models] final class OpenAICompatibleChatClient(settings: ModelApiSettings) extends ChatCompletionClient:
  private val client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
    .followRedirects(HttpClient.Redirect.NEVER).build()
  override def complete(input: ChatCompletionInput): IO[String] = request(input,settings.maxTokens,12000,65536,settings.timeoutSeconds).map(_.content)
  override def completeScene(input: ChatCompletionInput,maxTokens: Int): IO[SceneCompletionResult] =
    if maxTokens<1 || maxTokens>6000 then IO.raiseError(ModelRequestFailure(ModelFailureCode.InvalidInput))
    else request(input,maxTokens,90000,262144,180)
  private def request(input: ChatCompletionInput,maxTokens: Int,maxContent: Int,maxResponse: Int,timeoutSeconds: Int): IO[SceneCompletionResult] = IO.defer {
    def message(role: String, content: String): Json = Json.obj("role" -> Json.fromString(role), "content" -> Json.fromString(content))
    def userMessage: Json =
      require(input.images.size <= 6,"Too many model screenshots")
      val encodedSize=input.images.map(_.base64Data.length.toLong).sum
      require(encodedSize <= 8L*1024*1024,"Model screenshots exceed size limit")
      val screenshots=input.images.map { image =>
        require(Set("image/png","image/jpeg")(image.mediaType),"Unsupported screenshot format")
        val bytes=java.util.Base64.getDecoder.decode(image.base64Data)
        val valid=if image.mediaType == "image/png" then bytes.take(8).sameElements(Array[Byte](-119,80,78,71,13,10,26,10))
          else bytes.length >= 3 && bytes(0) == (-1).toByte && bytes(1) == (-40).toByte && bytes(2) == (-1).toByte
        require(valid,"Invalid screenshot content")
        Json.obj("type" -> Json.fromString("image_url"),"image_url" -> Json.obj("url" -> Json.fromString(s"data:${image.mediaType};base64,${image.base64Data}")))
      }
      if screenshots.isEmpty then message("user",input.user) else Json.obj("role" -> Json.fromString("user"),"content" -> Json.fromValues(
        Vector(Json.obj("type" -> Json.fromString("text"),"text" -> Json.fromString(input.user))) ++ screenshots))
    val messages = Vector(message("system",input.system)) ++ input.history.flatMap(turn =>
      Vector(message("user",turn.user),message("assistant",turn.assistant))) :+ userMessage
    val basic = Json.obj("model" -> Json.fromString(settings.model), "stream" -> Json.False,
      "max_tokens" -> Json.fromInt(maxTokens), "response_format" -> Json.obj("type" -> Json.fromString("json_object")),
      "messages" -> Json.fromValues(messages))
    val body = if settings.disableThinking then basic.deepMerge(Json.obj("thinking" -> Json.obj("type" -> Json.fromString("disabled")))) else basic
    IO.interruptibleMany {
      val request = HttpRequest.newBuilder(URI.create(settings.baseUrl.stripSuffix("/") + "/chat/completions"))
        .timeout(Duration.ofSeconds(timeoutSeconds)).header("Content-Type", "application/json")
        .header("Authorization", "Bearer " + settings.apiKey)
        .POST(HttpRequest.BodyPublishers.ofString(body.noSpaces, StandardCharsets.UTF_8)).build()
      val response = client.send(request, HttpResponse.BodyHandlers.ofInputStream())
      Using.resource(response.body()) { stream =>
        val bytes = stream.readNBytes(maxResponse+1)
        val status=response.statusCode()
        def failed(code: ModelFailureCode)=ModelRequestFailure(code,providerStatus=Some(status),responseBytes=Some(bytes.length),maxTokens=Some(maxTokens))
        if status!=200 then throw failed(ModelFailureCode.ProviderHttpError)
        if bytes.length>maxResponse then throw failed(ModelFailureCode.ResponseTooLarge)
        val json=parser.parse(new String(bytes,StandardCharsets.UTF_8)).fold(_=>throw failed(ModelFailureCode.InvalidEnvelope),identity)
        val choice=json.hcursor.downField("choices").downArray
        if choice.success.isEmpty then throw failed(ModelFailureCode.InvalidEnvelope)
        val finish=choice.get[String]("finish_reason").toOption.map(reason=>if Set("stop","length","content_filter","tool_calls","function_call")(reason) then reason else "other")
        val content=choice.downField("message").get[String]("content").toOption
        val tokens=json.hcursor.downField("usage").get[Long]("total_tokens").toOption.filter(_>=0).getOrElse(0L)
        def completionFailure(code: ModelFailureCode)=failed(code).copy(finishReason=finish,contentChars=content.map(_.length),totalTokens=tokens,partialContent=content.map(_.take(maxContent)))
        if !finish.contains("stop") then throw completionFailure(if finish.isEmpty then ModelFailureCode.InvalidEnvelope else ModelFailureCode.IncompleteOutput)
        val completed=content.filter(_.trim.nonEmpty).getOrElse(throw completionFailure(ModelFailureCode.EmptyOutput))
        if completed.length>maxContent then throw completionFailure(ModelFailureCode.ContentTooLarge)
        SceneCompletionResult(completed,tokens)
      }
    }.timeout(timeoutSeconds.seconds)
  }
      // Only allowlisted metadata is logged. Do not log exception messages or provider bodies.
      .handleErrorWith { error =>
        val failure=error match
          case known: ModelRequestFailure => known
          case _: java.util.concurrent.TimeoutException | _: java.net.http.HttpTimeoutException => ModelRequestFailure(ModelFailureCode.Timeout,maxTokens=Some(maxTokens))
          case _: IllegalArgumentException => ModelRequestFailure(ModelFailureCode.InvalidInput,maxTokens=Some(maxTokens))
          case _ => ModelRequestFailure(ModelFailureCode.TransportError,maxTokens=Some(maxTokens))
        IO.println(s"[model] Request failed (code=${failure.code}, status=${failure.providerStatus.getOrElse(0)}, finish=${failure.finishReason.getOrElse("missing")}, responseBytes=${failure.responseBytes.getOrElse(0)}).") *>
          IO.raiseError(failure)
      }
