package services.models.utils

import cats.effect.IO
import io.circe.parser.decode
import java.net.URI
import java.nio.file.{Files, Path}
import services.models.objects.ModelApiSettings

private[models] object ModelSettingsLoader:
  private val defaultSource = """{"baseUrl":"https://tokenhub.tencentmaas.com/v1","apiKey":"","model":"deepseek-v4.1-flash","timeoutSeconds":20,"maxTokens":1024,"disableThinking":true}"""
  private val tokenHubHosts = Set("tokenhub.tencentmaas.com","tokenhub.tencentmaas.cn","tokenhub-intl.tencentmaas.com","tokenhub-intl.tencentmaas.cn")

  private[models] def load(path: Path): IO[ModelApiSettings] = IO.blocking {
    val source = if Files.exists(path) then Files.readString(path) else defaultSource
    resolve(source,name => Option(System.getenv(name)))
  }

  // Keep provider credential selection explicit: an old direct-provider key must not be sent to TokenHub.
  private[models] def resolve(source: String,environment: String => Option[String]): ModelApiSettings =
    val decoded = decode[ModelApiSettings](source.stripPrefix("\uFEFF")).fold(_ => throw new IllegalArgumentException("Invalid model settings"), identity)
    val host=Option(URI.create(decoded.baseUrl).getHost).getOrElse("").toLowerCase(java.util.Locale.ROOT)
    val variable=if tokenHubHosts(host) then "TOKENHUB_API_KEY" else "DETECTIVE_MODEL_API_KEY"
    val key=environment(variable).filter(_.trim.nonEmpty).getOrElse(decoded.apiKey).trim
    checked(decoded.copy(apiKey=key))

  private[models] def checked(settings: ModelApiSettings): ModelApiSettings =
    val uri = URI.create(settings.baseUrl)
    val local = Set("127.0.0.1", "localhost", "::1")(Option(uri.getHost).getOrElse(""))
    require(uri.getScheme == "https" || (uri.getScheme == "http" && local), "Model URL requires HTTPS except loopback tests")
    require(uri.getUserInfo == null && uri.getQuery == null && uri.getFragment == null && uri.getHost != null)
    require(settings.apiKey.nonEmpty && !settings.apiKey.exists(c => c == '\n' || c == '\r'))
    require(settings.model.trim.nonEmpty && settings.model.length <= 100)
    require(settings.timeoutSeconds >= 2 && settings.timeoutSeconds <= 60 && settings.maxTokens >= 128 && settings.maxTokens <= 2048)
    settings
