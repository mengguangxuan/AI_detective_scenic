package services.cases.objects

import io.circe.{Decoder, Encoder}
import io.circe.generic.semiauto.*
import services.system.objects.*
import services.cases.objects.*


final case class FixtureOperation(id: OperationId, point: PointId, effects: Vector[FixtureEffect], label: String, repeatable: Boolean = false, openedRequirement: Option[EntityOpenRequirement] = None,
  requiredClues: Set[ClueId] = Set.empty)

object FixtureOperation:
  given Encoder[FixtureOperation] = deriveEncoder[FixtureOperation]
  given Decoder[FixtureOperation] = Decoder.instance { cursor =>
    for
      id <- cursor.get[OperationId]("id")
      point <- cursor.get[PointId]("point")
      effects <- cursor.get[Vector[FixtureEffect]]("effects")
      label <- cursor.get[String]("label")
      repeatable <- cursor.get[Option[Boolean]]("repeatable")
      requirement <- cursor.get[Option[EntityOpenRequirement]]("openedRequirement")
      clues <- cursor.get[Option[Set[ClueId]]]("requiredClues")
    yield FixtureOperation(id,point,effects,label,repeatable.getOrElse(false),requirement,clues.getOrElse(Set.empty))
  }
