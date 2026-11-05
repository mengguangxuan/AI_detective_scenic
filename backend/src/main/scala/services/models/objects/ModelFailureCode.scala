package services.models.objects
import io.circe.Encoder
// Finite transport/response failures, independent of case content.
enum ModelFailureCode:
  case ProviderHttpError,ResponseTooLarge,InvalidEnvelope,IncompleteOutput,EmptyOutput,ContentTooLarge,Timeout,InvalidInput,TransportError
object ModelFailureCode:
  given Encoder[ModelFailureCode]=Encoder.encodeString.contramap(_.toString)
