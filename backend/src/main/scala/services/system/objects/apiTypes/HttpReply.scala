package services.system.objects.apiTypes
import io.circe.Json
// Transport-only reply; cookie tokens are never serialized into the public JSON body.
final case class HttpReply(body: Json, cookie: Option[String] = None)
