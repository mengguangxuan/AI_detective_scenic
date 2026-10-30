package services.games.utils

import services.cases.objects.{CaseContent, SceneNodeId}
import services.cases.objects.scene.FixturePoint
import services.games.objects.GameSessionRecord
import services.system.objects.Vec3

// Visibility and entry reachability share one authored, translation-only parent chain.
private[games] object SceneAccessRules:
  private def lookup(content: CaseContent, state: GameSessionRecord): SceneNodeId => Option[Vec3] =
    val nodes = content.nodes.map(n => n.id -> n).toMap
    def resolve(id: SceneNodeId, seen: Set[SceneNodeId]): Option[Vec3] =
      nodes.get(id).filter(n => !seen(id) && n.place == state.playerPlace && state.visibleNodes(n.id)).flatMap { n =>
        n.parent match
          case None => Some(n.position)
          case Some(parent) => resolve(parent,seen + id).map(p => Vec3(p.x+n.position.x,p.y+n.position.y,p.z+n.position.z))
      }
    id => resolve(id,Set.empty)

  private[games] def positions(content: CaseContent, state: GameSessionRecord): Map[SceneNodeId,Vec3] =
    val position = lookup(content,state)
    content.nodes.flatMap(n => position(n.id).map(n.id -> _)).toMap

  private[games] def withinRange(position: Option[Vec3], player: Vec3, point: FixturePoint): Boolean =
    position.exists { p =>
      val dx=p.x+point.localPoint.x-player.x
      val dy=p.y+point.localPoint.y-player.y
      val dz=p.z+point.localPoint.z-player.z
      dx*dx+dy*dy+dz*dz <= point.radiusMeters*point.radiusMeters
    }

  private[games] def accessible(content: CaseContent, state: GameSessionRecord, point: FixturePoint): Boolean =
    withinRange(lookup(content,state)(point.node),state.playerPosition,point)
