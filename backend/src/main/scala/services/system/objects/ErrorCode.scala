package services.system.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*

// Finite failures handled by this slice, not narrative categories.
enum ErrorCode:
  case ServerFailure, Unauthenticated, InvalidValue, UnsupportedOperation, AccessDenied, ConditionFailed, StaleState, RequestConflict, RequestPending, ReplayVersionMismatch, ConflictingContent

object ErrorCode:
  def toString(value: ErrorCode): String = value.productPrefix
  def fromString(value: String): Either[String, ErrorCode] =
    ErrorCode.values.find(toString(_) == value).toRight("Unknown error code")
  given Encoder[ErrorCode] = Encoder.encodeString.contramap(toString)
  given Decoder[ErrorCode] = Decoder.decodeString.emap(fromString)
