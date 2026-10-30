package services.cases.utils

import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import io.circe.syntax.*
import services.cases.objects.*

// Read-only compatibility for the old embedded police object. All runtime actors
// use WitnessProfile; missing legacy disclosures are compiled once at decoding.
private[cases] object LegacyPoliceProfile:
  private[cases] def decode(raw: Json, routes: Vector[FixtureRoute], clues: Vector[FixtureClue]): Decoder.Result[WitnessProfile] =
    val c = raw.hcursor
    for
      point <- c.get[PointId]("point")
      name <- c.get[String]("name")
      background <- c.get[String]("background")
      id <- c.get[Option[WitnessId]]("id")
      voice <- c.get[Option[String]]("voice")
      dossier <- c.get[Option[DialogueDossier]]("dossier")
      answers <- c.get[Option[Vector[WitnessAnswer]]]("answers")
    yield WitnessProfile(id.getOrElse(WitnessId("melchett")),point,name,background,
      voice.getOrElse("直率、务实，清楚区分供述、医生意见和待核实的猜测。"),
      answers.filter(_.nonEmpty).getOrElse(routes.filter(_.point == point).map(r =>
        WitnessAnswer(WitnessAnswerId(r.id.value.stripPrefix("route_")),
          clues.filter(c => r.clues.contains(c.id)).map(_.text.value).mkString("\n"),Vector(r.id),r.requiredClues))),
      dossier.getOrElse(DialogueDossier(Vector.empty,Vector.empty,Vector.empty)))

  private[cases] def normalize(cursor: HCursor): Decoder.Result[Json] =
    cursor.downField("police").focus.filter(_.isObject) match
      case None => Right(cursor.value)
      case Some(raw) =>
        for
          routes <- cursor.get[Vector[FixtureRoute]]("routes")
          clues <- cursor.get[Vector[FixtureClue]]("clues")
          witnesses <- cursor.get[Option[Vector[WitnessProfile]]]("witnesses").map(_.getOrElse(Vector.empty))
          police <- decode(raw,routes,clues)
          _ <- Either.cond(!witnesses.exists(_.id == police.id),(),
            DecodingFailure("police：旧警方身份与人物问询ID重复",cursor.history))
        yield cursor.value.mapObject(_.add("witnesses",(witnesses :+ police).asJson).add("police",police.id.asJson))
