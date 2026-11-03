package services.cases.objects.investigation
import io.circe.{Decoder,Encoder,Json}
import io.circe.generic.semiauto.*
import services.cases.objects.OperationId

final case class OperationDuration(operation: OperationId, seconds: Int)
object OperationDuration:
  given Encoder[OperationDuration] = deriveEncoder[OperationDuration]
  given Decoder[OperationDuration] = deriveDecoder[OperationDuration]
// These are public planning markers; they do not schedule or execute future events.
final case class InvestigationTimeTarget(label: String, seconds: Long)
object InvestigationTimeTarget:
  given Encoder[InvestigationTimeTarget] = deriveEncoder[InvestigationTimeTarget]
  given Decoder[InvestigationTimeTarget] = deriveDecoder[InvestigationTimeTarget]
final case class InvestigationClock(startMinutesOfDay: Int, actionSeconds: Int, startWeekday: Option[Int] = None,
  operationDurations: Vector[OperationDuration] = Vector.empty, playableUntilSeconds: Option[Long] = None,
  targets: Vector[InvestigationTimeTarget] = Vector.empty)
object InvestigationClock:
  given Encoder[InvestigationClock] = deriveEncoder[InvestigationClock].mapJson(_.mapObject(fields =>
    Vector("startWeekday","operationDurations","playableUntilSeconds","targets").foldLeft(fields)((o,key) =>
      if o(key).exists(j => j == Json.Null || j.asArray.exists(_.isEmpty)) then o.remove(key) else o)))
  given Decoder[InvestigationClock] = deriveDecoder[InvestigationClock].prepare(_.withFocus(_.mapObject(fields =>
    Vector("operationDurations","targets").foldLeft(fields)((o,key) => if o.contains(key) then o else o.add(key,Json.arr())))))
