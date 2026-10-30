package services.cases.objects.investigation

import io.circe.{Decoder, Encoder}

// Closed submission protocol for the time-comparison form, not a taxonomy of case facts.
enum TimeAssessment:
  case Consistent, Conflict, Uncertain
object TimeAssessment:
  def toString(value: TimeAssessment): String = value match
    case TimeAssessment.Consistent => "consistent"
    case TimeAssessment.Conflict => "conflict"
    case TimeAssessment.Uncertain => "uncertain"
  def fromString(value: String): Either[String, TimeAssessment] = value match
    case "consistent" => Right(TimeAssessment.Consistent)
    case "conflict" => Right(TimeAssessment.Conflict)
    case "uncertain" => Right(TimeAssessment.Uncertain)
    case _ => Left("Unsupported time assessment")
  given Encoder[TimeAssessment] = Encoder.encodeString.contramap(toString)
  given Decoder[TimeAssessment] = Decoder.decodeString.emap(fromString)
