package services.cases.utils

import cats.syntax.all.*
import io.circe.{Decoder, DecodingFailure, HCursor, Json}
import services.cases.objects.*

private[cases] object CharacterConfiguration:
  private[cases] def validDossier(d: DialogueDossier, requireKnowledge: Boolean): Boolean =
    (!requireKnowledge || d.knowledge.nonEmpty) &&
      Vector(d.knowledge,d.privateContext,d.policy).forall(xs => xs.size <= 30 && xs.forall(s => s.trim.nonEmpty && s.length <= 1200))

  private[cases] def decode(root: HCursor, background: Json, investigation: Json): Decoder.Result[(Vector[CasePersonBrief],Vector[WitnessProfile],Option[PoliceContact])] =
    def check(ok: Boolean, path: String, message: String): Decoder.Result[Unit] =
      Either.cond(ok,(),DecodingFailure(s"$path：$message",root.history))
    if !root.downField("characters").succeeded then
      // Existing authoring and stored revisions retain their original compiled shape.
      for
        people <- background.hcursor.get[Vector[CasePersonBrief]]("persons")
        witnesses <- investigation.hcursor.get[Option[Vector[WitnessProfile]]]("witnesses")
        police <- investigation.hcursor.get[Option[PoliceContact]]("police")
      yield (people,witnesses.getOrElse(Vector.empty),police)
    else
      for
        characters <- root.get[Vector[CharacterProfile]]("characters")
        interactions <- investigation.hcursor.get[Vector[WitnessInteraction]]("interviews")
        rawPolice <- investigation.hcursor.get[Option[Json]]("police")
        policeId <- rawPolice.traverse { p =>
          check(p.asObject.exists(_.keys.toSet == Set("witness")),"investigation.police","只指定问询ID，不重复填写人物资料") *>
            p.hcursor.get[WitnessId]("witness")
        }
        oldPeople <- background.hcursor.get[Option[Vector[Json]]]("persons")
        oldWitnesses <- investigation.hcursor.get[Option[Vector[Json]]]("witnesses")
        _ <- check(oldPeople.forall(_.isEmpty) && oldWitnesses.forall(_.isEmpty),"characters","不能同时维护旧人物列表和统一人物资料")
        _ <- check(characters.map(_.id).distinct.size == characters.size,"characters","人物ID重复")
        _ <- check(interactions.map(_.id).distinct.size == interactions.size,"investigation.interviews","问询ID重复")
        _ <- check(interactions.map(_.person).distinct.size == interactions.size,"investigation.interviews.person","同一人物不能重复绑定问询身份")
        _ <- characters.zipWithIndex.traverse_ { case (p,index) =>
          check(Vector(p.name,p.identity,p.victimRelation,p.caseRelation,p.background,p.voice).forall(_.trim.nonEmpty),s"characters[$index]","人物资料不能为空") *>
            check(validDossier(p.dossier,false),s"characters[$index].dossier","资料条数或长度不合法")
        }
        profiles <- interactions.zipWithIndex.traverse { case (w,index) =>
          for
            person <- characters.find(_.id == w.person).toRight(DecodingFailure(s"investigation.interviews[$index].person：人物不存在",root.history))
            _ <- check(validDossier(person.dossier,true),s"characters[${characters.indexOf(person)}].dossier.knowledge","可问询人物必须有本人资料")
          yield WitnessProfile(w.id,w.point,person.name,person.background,person.voice,w.answers,person.dossier)
        }
        _ <- check(policeId.forall(id => profiles.exists(_.id == id)),"investigation.police.witness","警方问询身份不存在")
        _ <- profiles.traverse_(p => check(p.answers.nonEmpty,"investigation.interviews.answers","问询对象需要合法披露配置"))
        policeProfile = policeId.flatMap(id => profiles.find(_.id == id))
        police = policeProfile.map(p => PoliceContact(p.point,p.name,p.background,p.id,p.voice,p.dossier,p.answers))
        people = characters.map(p => CasePersonBrief(p.id,p.name,p.identity,p.victimRelation,p.caseRelation,
          interactions.find(_.person == p.id).map(_.id)))
      yield (people,profiles.filterNot(p => policeId.contains(p.id)),police)
