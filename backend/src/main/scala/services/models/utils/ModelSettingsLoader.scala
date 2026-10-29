package services.models.utils

import cats.effect.IO
import io.circe.parser.decode
import java.net.URI
import java.nio.file.{Files, Path}
import services.models.objects.ModelApiSettings

private[models] object ModelSettingsLoader:
  private[models] def load(path: Path): IO[ModelApiSettings] = IO.blocking {
    // The environment can hold the secret while the other settings stay in the local file.
    val source = if Files.exists(path) then Files.readString(path) else
      """{"baseUrl":"https://api.deepseek.com","apiKey":"","model":"deepseek-flash","timeoutSeconds":20,"maxTokens":512,"disableThinking":true}"""
    val decoded = decode[ModelApiSettings](source.stripPrefix("\uFEFF")).fold(_ => throw new IllegalArgumentException("Invalid model settings"), identity)
    val settings = decoded.copy(apiKey=Option(System.getenv("DETECTIVE_MODEL_API_KEY")).filter(_.trim.nonEmpty).getOrElse(decoded.apiKey).trim)
    checked(settings)
  }

  private[models] def checked(settings: ModelApiSettings): ModelApiSettings =
    val uri = URI.create(settings.baseUrl)
    val local = Set("127.0.0.1", "localhost", "::1")(Option(uri.getHost).getOrElse(""))
    require(uri.getScheme == "https" || (uri.getScheme == "http" && local), "Model URL requires HTTPS except loopback tests")
    require(uri.getUserInfo == null && uri.getQuery == null && uri.getFragment == null && uri.getHost != null)
    require(settings.apiKey.nonEmpty && !settings.apiKey.exists(c => c == '\n' || c == '\r'))
    require(settings.model.trim.nonEmpty && settings.model.length <= 100)
    require(settings.timeoutSeconds >= 2 && settings.timeoutSeconds <= 60 && settings.maxTokens >= 128 && settings.maxTokens <= 2048)
    settings
