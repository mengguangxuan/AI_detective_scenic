package services.models.api

import cats.effect.IO
import java.nio.file.Path
import services.models.objects.{ChatCompletionInput,ModelApiSettings}
import services.models.utils.{OpenAICompatibleChatClient, ModelSettingsLoader}

// Outbound transport contract (not a browser API). It deliberately has no JDBC argument.
// Implement this interface to replace the provider or inject a deterministic test service.
trait ChatCompletionClient:
  def complete(input: ChatCompletionInput): IO[String]

object ChatCompletionClient:
  val unconfigured: ChatCompletionClient = new ChatCompletionClient:
    override def complete(input: ChatCompletionInput): IO[String] = IO.raiseError(new IllegalStateException("Model not configured"))

  def configured(path: Path): IO[ChatCompletionClient] =
    ModelSettingsLoader.load(path).map(settings => new OpenAICompatibleChatClient(settings): ChatCompletionClient)
      .handleErrorWith(_ => IO.println("[model] Configuration missing or invalid; fixed questions remain available.").as(unconfigured))

  def fromSettings(settings: ModelApiSettings): IO[ChatCompletionClient] =
    IO(ModelSettingsLoader.checked(settings)).map(value => new OpenAICompatibleChatClient(value): ChatCompletionClient)
      .handleErrorWith(_ => IO.raiseError(new IllegalArgumentException("Invalid model configuration")))
