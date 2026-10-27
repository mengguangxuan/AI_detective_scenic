# 后端领域类型修改意见（针对 `case_truth_types.md` v1.2）

> **本文性质**：对 v1.2 的评审结论与修改提案，供作者决定是否形成 v1.3。文中代码块是**契约草案**，不是已生成的 Scala 源码。
>
> **评审依据**：
> - 课程规范：`../../../samples/samples/rules.txt`（下称"规则 N"）
> - 功能目标：`../../../plan.md`
> - 参考写法：`../../../samples/samples/sample/backend-sample/`
> - v1 结构参考：`../cases/murder_at_the_vicarage.md`
>
> **v1 范围前提**（本意见按此前提裁剪）：
> 1. 只做侦探模式；所有嫌疑人由 AI 驱动，无"玩家扮演嫌疑人"。
> 2. 单一预置案件，采纳 `plan.md` 5.1 的"暴雨夜封闭书房"结构：**一具尸体、单一现场、侦探到场后世界静态**。
> 3. 场景由人工 + AI 共建，不做 AI 直接生成 3D。
> 4. 嫌疑模式（`GameMode.SuspectChallenge`）留到第二版。

---

## 0. 结论摘要

v1.2 在**领域建模**层面的质量显著高于课程平均线：三条时间线、真实/主观事件分离、AI 草稿与已提交事实分离、可重放日志、枚举有限性的逐条论证，都是可以保留并作为答辩亮点的设计。

但它目前是一份**纯领域草案**，缺两类东西：

| 缺什么 | 影响 | 优先级 |
|---|---|---|
| **落地层**：API 消息、`apiTypes`、服务归属、编解码策略 | 直接不满足规则 20/24/2/9/14/17 | P0 |
| **运行层**：3D 坐标约定、玩家视图投影、场景共建交接物 | v1 的第 2 天（场景）与第 4 天（审讯）会卡住 | P0/P1 |

另有 5 项**会传染整条链路**的结构缺陷，建议在写第一行 Scala 之前修掉（第 2 节 P0）。

按 v1 的静态单案前提，v1.2 中有三块复杂度可以**直接不使用**（不是删除）：`ScheduledOccurrence`、`AdvanceTime`、多 `Incident` 的分别指控流程。这会显著降低第 11、15、16 节的实现成本。

---

## 1. 与课程规范的符合度

### 1.1 已符合（建议保留，不要改动）

| 规则 | 符合之处 |
|---|---|
| 19 有意识地覆盖有限状态 | §20.3 逐条论证每个枚举的有限边界，并明确拒绝 `EventKind`/`ClueKind`。这是全文最强的部分，建议在答辩中单独讲。 |
| 2 / 4 服务归属 | 第 1 节给出 `cases` / `games` / `accounts` / `system` 四模块划分思路。 |
| 8 / 12 utils 可见性 | 第 857 行明确"纯规则放所属服务 `utils`，`private[服务名]`"。 |
| 13 objects 纯粹性 | 全部类型无业务行为，未混入随机 ID、DB 访问、权限检查。 |
| 15 一类型一文件 | 第 24、857 行两次强调，方向正确。 |
| 23 避免转发层 | 未引入 Service 层，保持 `APIMessage -> Table`。 |
| 28 令牌信任边界 | 第 700 行"不接受客户端自报用户身份"，正确。 |
| 59 文档与源码分离 | 位于 `docs/backend/`，正确。 |

### 1.2 缺口 A：API 层完全缺失（规则 20 / 21 / 24 / 26 / 31 / 33）

**现状**：`*Message` 类型数量为 0，`objects/apiTypes/` 下响应类型数量为 0。第 18 节只有一句"路由将可信 `UserId` 与 `GameRequest` 构造成 API 消息"。

**问题 1（规则 20）**：第 18 节的 `PlayerCommand` 是**一个类型装了 6 个操作**：

```scala
enum PlayerCommand:
  case Investigate(point: PointId, input: InvestigationInput)
  case Execute(operation: OperationId)
  case Speak(person: PersonId, text: Text, presentedClues: Set[ClueId])
  case ReadReport(report: ReportId, route: RouteId)
  case Move(place: PlaceId, position: Vec3)
  case Submit(answer: Accusation)
```

规则 20 要求"每个 API 操作放在自己的文件中，作为业务行为的主要可执行单元，名称以 `Message` 结尾"。`PlayerCommand` 不能同时充当领域指令集和 API 输入。

**修改方案**：保留 `PlayerCommand` 作为**领域层的封闭指令集**（供纯函数 `planXxx` 使用），API 层另建消息：

```scala
// services/games/api/SubmitAccusationMessage.scala
final case class SubmitAccusationMessage(
  userId: UserId,
  sessionId: SessionId,
  expectedSequence: Sequence,
  answer: Accusation
) extends APIWithTokenMessage[SubmitAccusationResponse]:
  override def plan(connection: Connection): IO[SubmitAccusationResponse] = ???

// services/games/objects/apiTypes/SubmitAccusationResponse.scala
final case class SubmitAccusationResponse(
  record: RecordId, verdict: VerdictView, sessionStatus: SessionStatus
)
```

**问题 2（规则 24）**：`AccusationRecord(id, answer, verdict)` 是领域记录，含 `Verdict` 全量。对外必须投影——规则 24 要求"每个 API 只有一个返回结果；多个字段组织成一个响应数据结构"。

**问题 3（规则 21 / 22）**：`GameRequest(id, session, expectedSequence, command)` 的四个字段在 API 层应直接成为消息构造参数；`GameRequest` 本身不是 API 类型。

**问题 4（规则 31）**：`plan.md` 第 251-260 行的 REST 路径 `/api/games/:id/interrogate` 与规则 31 的"从消息名推导路由名"冲突。

**问题 5**：`GameError(code, publicMessage)` 与课程样本的 `system.HttpError` 关系未定义。

**建议的 v1 消息清单**（14 个，名称按规则 31 可推导路由名）：

| 服务 | 消息 | 返回类型（`apiTypes`） | 页面 |
|---|---|---|---|
| accounts | `GetCurrentUserMessage` | `CurrentUserResponse` | 全局 |
| accounts | `RegisterUserMessage` | `AuthResponse` | 03 |
| accounts | `LoginUserMessage` | `AuthResponse` | 03 |
| accounts | `LogoutUserMessage` | `LogoutResponse` | 03 |
| cases | `ListCasesMessage` | `CaseListResponse` | 02 |
| cases | `GetCaseBriefingMessage` | `CaseBriefingResponse` | 05 |
| games | `StartGameMessage` | `StartGameResponse` | 11 |
| games | `GetGameStateMessage` | `GameStateResponse` | 06/07 |
| games | `GetSceneViewMessage` | `SceneViewResponse` | **06** |
| games | `InvestigateMessage` | `InvestigateResponse` | 06 |
| games | `ExecuteOperationMessage` | `ExecuteOperationResponse` | 06 |
| games | `MoveMessage` | `MoveResponse` | 06 |
| games | `InterrogateMessage` | `InterrogateResponse` | **09** |
| games | `SubmitAccusationMessage` | `SubmitAccusationResponse` | 10 |
| games | `GetAccusationOptionsMessage` | `AccusationOptionsResponse` | 10 |
| games | `GetCaseReviewMessage` | `CaseReviewResponse` | 14 |
| games | `GetPlayerStatsMessage` | `PlayerStatsResponse` | 15 |

（页 05/08 的嫌疑人公开资料可并入 `CaseBriefingResponse`，避免为单字段造消息。）

**`GameError` 的处置**：保留为**领域错误**，在 `services/system/api/` 加一层共享映射（规则 4 允许，需在文档中说明理由：`cases` 与 `games` 都需要相同映射）：

```scala
// services/system/api/GameErrorMapping.scala
def toHttpError(error: GameError): HttpError = error.code match
  case ErrorCode.MissingReference     => HttpError.NotFound(error.publicMessage)
  case ErrorCode.InvalidValue         => HttpError.BadRequest(error.publicMessage)
  case ErrorCode.InvalidTime          => HttpError.BadRequest(error.publicMessage)
  case ErrorCode.ConflictingContent   => HttpError.Conflict(error.publicMessage)
  case ErrorCode.AccessDenied         => HttpError.Forbidden(error.publicMessage)
  case ErrorCode.ConditionFailed      => HttpError.Conflict(error.publicMessage)
  case ErrorCode.FutureInformation    => HttpError.Conflict(error.publicMessage)
  case ErrorCode.UnsupportedOperation => HttpError.BadRequest(error.publicMessage)
  case ErrorCode.StaleState           => HttpError.Conflict(error.publicMessage)
  case ErrorCode.RequestConflict      => HttpError.Conflict(error.publicMessage)
  case ErrorCode.InvalidAiOutput      => HttpError.unavailable(error.publicMessage)
  case ErrorCode.AiUnavailable        => HttpError.unavailable(error.publicMessage)
  case ErrorCode.UnreachableRequiredClue => HttpError.Unprocessable(error.publicMessage)
  case ErrorCode.ReplayVersionMismatch   => HttpError.Conflict(error.publicMessage)
  case ErrorCode.ValidationIncomplete    => HttpError.Conflict(error.publicMessage)
```

若样本的 `HttpError` 没有 `Forbidden` / `Unprocessable` / `unavailable`，按样本实际工厂方法调整。

---

### 1.3 缺口 B：类型未标注服务归属，调用边界过不了规则 9（规则 2 / 3 / 9 / 10 / 12）

**现状**：全部约 130 个类型堆在一个文档里，没有标注归哪个服务的 `objects/`。

**由此产生的真实矛盾**：第 857 行规定"纯规则放所属服务 `utils` 并采用 `private[服务名]` 可见性"，但：

| 纯函数 | 文档归属 | 实际需要它的人 | 冲突 |
|---|---|---|---|
| `validateCase` | cases | 需要跑推导检查证据组合 | 要用 `deriveFacts` |
| `deriveFacts` | games（`private[games]`） | `validateCase`（cases） | **规则 9/12 不允许** |
| `availableRoutes` | games | 需要加载 `CasePackage` 修订 | 需要 cases 的表/API |
| `buildPlayerView` | games | 大量 cases 侧类型 | 依赖方向需单向 |

**修改方案 1：类型归属表**（建议直接作为 v1.3 的新一章）

| 归属 | 类型 |
|---|---|
| `services/accounts/objects/` | `UserId`、`UserProfile` |
| `services/cases/objects/` | 全部 ID 值类型、`Text`/`Revision`/`Sequence`/`CaseTime`/`TimeWindow`/`Vec3`/`Rotation`/`Transform`、`Place`/`Connection`/`Environment`/`Entity`/`Relationship`/`SemanticScene`/`SceneRequirement`/`WorldSpec`、`EventAccount`/`Event`/`Motive`/`Responsibility`/`Resolution`/`Incident`、`Subject`/`Topic`/`Existence`/`FactValue`/`Fact`/`PrivateContext`/`Claim`、`PersonProfile`/`KnownClue`/`InformationSource`/`InitialAwareness`/`BeliefBasis`/`BelievedEvent`/`PersonPerspective`/`Person`、`PublicSpec` 全部、`Asset`/`SceneNode`/`SceneCue`/`Entry`/`InquiryPoint`/`SceneSpec`/`SceneReview`、`Clue`/`FindingContent`/`Condition`/`DiscoveryMethod`/`DiscoveryRoute`、`WorldEffect`/`OperationEffect`/`Operation`、`DeductionRule`/`Premise`/`SolutionRequirement`、`SourceSpec`、`CasePackage` |
| `services/games/objects/` | `SessionId`/`RequestId`/`RecordId`/`AiCallId`、`Actor`/`Observer`/`Speaker`、`GameSession`/`PlayerRole`/`GameMode`/`SessionStatus`、`WorldState`/`PlayerKnowledge`/`NpcState`/`GameState`、各类 `Record`、`LearnedInformation`/`InformationAcquisition`/`PerspectiveChange`/`NpcMemory`/`PlannedObservation`/`ScheduledOccurrence`、全部 AI 上下文与草稿类型、`SessionChange`/`SessionCommit`/`Snapshot`/`RequestReceipt`、`Accusation`/`RequirementResult`/`Verdict`/`AccusationRecord` |
| `services/system/objects/` | `NonEmptyVector`、共享 codec、`ValidationIssue`/`ErrorCode`/`GameError` |

**修改方案 2：依赖方向声明**（必须写死在文档里）

```
games  ──依赖──▶  cases  ──依赖──▶  system
accounts ──依赖──▶  system
                    ▲
                    └── games / cases 均可依赖
```

`cases` **禁止**依赖 `games`。若某规则被两侧需要（如 `deriveFacts`），按方案 3 处理。

**修改方案 3：把跨服务纯函数提升为公开 API 消息**

```scala
// services/cases/api/DeriveFactsMessage.scala
final case class DeriveFactsMessage(
  caseId: CaseId, caseRevision: Revision,
  knownFacts: Set[FactId], occurredEvents: Set[EventId], knownClues: Set[ClueId]
) extends APIMessage[DeriveFactsResponse]:
  override def plan(connection: Connection): IO[DeriveFactsResponse] = ???
```

`validateCase`（cases 内部）与 `deriveFacts`（games 调用）都走 `DeriveFactsMessage(...).plan(connection)`，符合规则 9/10，且不破坏封装。

**同时需要补充**：`games` 运行前必须能读到案件修订。目前没有任何"读取 `CasePackage`"的 API 或 Table。建议：

```scala
// services/cases/api/LoadCasePackageMessage.scala
final case class LoadCasePackageMessage(caseId: CaseId, revision: Revision)
  extends APIMessage[CasePackage]
```

（返回完整聚合是允许的，因为 `plan` 是**后端内部**边界；面向前端的投影另由 `buildPlayerView` 负责。这一点要在文档中写明，否则容易被误读为"泄露真相"。）

---

### 1.4 缺口 C：编解码策略未分类，且 `Map` 键会导致编译失败（规则 14 / 17 / 18）

**问题 1（会在第一天编译失败）**：v1.2 有 6 处自定义 ID 作为 `Map` 的键：

```
WorldState.entityPlaces   : Map[EntityId, PlaceId]
WorldState.nodeOverrides  : Map[SceneNodeId, Transform]
GameState.occurredEvents  : Map[EventId, CaseTime]
GameState.presented       : Map[PersonId, Set[ClueId]]
GameState.npcs            : Map[PersonId, NpcState]
InvestigationAiContext.approvedResults : Map[ClueId, FindingContent]
```

Circe 的 `deriveEncoder` 对这些字段需要 `KeyEncoder[XId]`，**它不会从 `case class XId(value: String)` 自动推出**。第 2 节只说"同名伴生对象放编解码器"，未提 `KeyEncoder`。建议把 ID 的伴生对象模板写成：

```scala
final case class EntityId(value: String)

object EntityId:
  def toString(id: EntityId): String = id.value

  def fromString(value: String): Either[String, EntityId] =
    val trimmed = value.trim
    if trimmed.isEmpty then Left("EntityId 不能为空") else Right(EntityId(trimmed))

  given Encoder[EntityId] = Encoder.encodeString.contramap(toString)
  given Decoder[EntityId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[EntityId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[EntityId] = KeyDecoder.decodeKeyString.emap(fromString)
```

全部 20 余个 ID 类型按此模板，`Text` 同（非空校验放 `fromString`）。这样"ID/Text 非空"由**解码器**保证，而不只靠 `validateCase`。

**问题 2（规则 14）**：`NonEmptyVector` 只被描述为"项目统一的集合实现"，没有 codec。建议改为项目自有类型，使伴生对象能持有 codec（同时消掉对 `cats.data` 的依赖）：

```scala
final case class NonEmptyVector[+A](head: A, tail: Vector[A]):
  def toVector: Vector[A] = head +: tail

object NonEmptyVector:
  def of[A](head: A, tail: A*): NonEmptyVector[A] = NonEmptyVector(head, tail.toVector)
  def fromVector[A](values: Vector[A]): Option[NonEmptyVector[A]] =
    values.headOption.map(h => NonEmptyVector(h, values.tail))

  given [A](using Encoder[A]): Encoder[NonEmptyVector[A]] =
    Encoder.encodeVector.contramap(_.toVector)
  given [A](using Decoder[A]): Decoder[NonEmptyVector[A]] =
    Decoder.decodeVector[A].emap {
      case h +: t => Right(NonEmptyVector(h, t))
      case _      => Left("NonEmptyVector 不能为空")
    }
```

**问题 3（规则 17 / 18）**：22 个枚举没有分类。带载荷枚举硬套 `toString`/`fromString` 会丢信息。建议在文档中明确二分：

| 类别 | 枚举 | 编解码 |
|---|---|---|
| **无载荷**：走 `toString`/`fromString`，与规则 18 完全一致 | `Existence`、`AssetKind`、`DiscoveryMethod` 的非参数分支、`Observer` 的非参数分支、`AiCallStatus`、`RequestStatus`、`GameMode`、`SessionStatus`、`SourceKind`、`ErrorCode` | 伴生对象：`def toString` / `def fromString` + `Encoder.encodeString.contramap` / `Decoder.decodeString.emap` |
| **带载荷**：**不得**字符串化，走结构化 JSON（`deriveEncoder`/`deriveDecoder`，`type` 判别字段固定为 `kind`） | `Actor`、`Speaker`、`Subject`、`InformationSource`、`FactValue`、`Entry`、`Condition`、`Premise`、`DiscoveryMethod`、`Observer`、`WorldEffect`、`OperationEffect`、`SessionChange`、`AiDecision`、`InvestigationInput`、`PlayerCommand` | 伴生对象：`given Encoder[X] = deriveEncoder`（需统一 `Configuration` 或 `Discriminator("kind")`） |

混合枚举（同时有参数分支与无参数分支，如 `Observer`、`Actor`、`Speaker`、`DiscoveryMethod`）一律按**带载荷**处理，保持编码一致。

**问题 4（规则 7）**：第 73 行"禁止 Any、任意 JSON 参数袋"与 `SessionCommit.changes: NonEmptyVector[SessionChange]` 的落库方式有张力。建议在文档补一句：

> `SessionChange` 以结构化 JSON 存 `jsonb` 列，由伴生对象统一编解码，不接受自由键值；各类 `Record` 本体仍落各自的表，日志只存引用与顺序（对应 §16.2 规则 8）。

---

## 2. P0 类型补丁（写第一行 Scala 之前必须完成）

### P0-1　`SolutionRequirement` 与 `Verdict` 缺标签

`plan.md` 页 10 要"凶手、动机、作案手法、证据链"分开判定，页 14 要显示"哪些判错了"。现状：

```scala
final case class SolutionRequirement(incident, conclusion, acceptedClueSets, weight)  // 无标签
final case class RequirementResult(conclusion, passed, awardedPoints)                 // 无 incident、无标签
```

**补丁**：

```scala
final case class SolutionRequirement(
  incident: IncidentId, conclusion: FactId, label: Text,   // 新增：语义标签，如"凶手身份"
  acceptedClueSets: NonEmptyVector[NonEmptyVector[ClueId]], weight: Int
)

final case class RequirementResult(
  incident: IncidentId, conclusion: FactId, label: Text,   // 新增
  passed: Boolean, awardedPoints: Int
)
```

`label` 用 `Text` 而非枚举——它属于开放语义，不是程序协议，符合 §20.3 的原则。

### P0-2　缺少"指控候选集"，页 10 无法渲染选项

`SolutionRequirement.conclusion` 是正确答案，但"错误选项从哪来"没有类型。补：

```scala
final case class AccusationCandidate(fact: FactId, label: Text, incident: IncidentId)

final case class AccusationOptions(
  incident: IncidentId,
  requirements: NonEmptyVector[SolutionRequirement],
  candidates: NonEmptyVector[AccusationCandidate]   // 同一 requirement 的候选集合
)
```

由 `GetAccusationOptionsMessage` 下发，且必须**过滤掉会泄露真相的候选**（例如不能把"凶手是 X"作为候选 label 直接暴露给还没找到相关线索的玩家）。建议：候选集 = 该 `Incident.affectedPeople` 的交集 + 由作者显式配置，优先后者。

### P0-3　场景共建缺交接物类型（v1 的核心交付物）

v1 改为人工 + AI 共建场景后，`buildSceneAuthoringInput`（第 844 行，只有函数名）从"护栏"变成"施工图纸"，必须实体化：

```scala
final case class SceneBounds(min: Vec3, max: Vec3)

final case class AssetLibrary(
  models: NonEmptyVector[AssetId],
  images: NonEmptyVector[AssetId],
  audio:  NonEmptyVector[AssetId],
  materials: NonEmptyVector[AssetId]
)

final case class SceneAuthoringInput(
  caseId: CaseId, caseRevision: Revision,
  scene: SemanticScene,
  requirements: NonEmptyVector[SceneRequirement],
  entities: NonEmptyVector[Entity],
  relevantFacts: NonEmptyVector[FactId],      // 仅允许制作 AI 知道的事实，不是整案真相
  cues: Vector[SceneCue],
  supportedOperations: NonEmptyVector[OperationId],
  allowedAssets: AssetLibrary,
  bounds: SceneBounds,
  freeRein: Vector[Text]                      // 对应 decorativeNotes：可自由发挥的美术项
)
```

同时补"未检查"状态。第 385 行用"Vector 缺项 = 未检查"表达，重建 review 时看不出哪些是新增要求：

```scala
enum ReviewOutcome:
  case Pending, Passed, Failed

final case class RequirementReview(
  requirement: SceneRequirementId, outcome: ReviewOutcome,
  method: Text, evidence: Vector[AssetId], notes: Text
)
```

并规定：**存在 `Pending` 或 `Failed` 即不得发布场景**（对应 §20.2 清单）。

### P0-4　缺少玩家视图（页 06 无法实现）

第 855 行 `buildPlayerView` 只有一句话。页 06 需要的东西没有类型：

```scala
final case class SceneNodeView(
  id: SceneNodeId, asset: AssetId, worldTransform: Transform, visible: Boolean)

final case class InquiryPointView(
  id: PointId, worldPosition: Vec3, radiusMeters: Double,
  label: Text, available: Boolean, operation: Option[OperationId])

final case class ConnectionView(
  to: PlaceId, bidirectional: Boolean, travelSeconds: Int, open: Boolean)

final case class PlayerSceneView(
  place: PlaceId, placeName: Text,
  nodes: NonEmptyVector[SceneNodeView],
  points: Vector[InquiryPointView],
  exits: Vector[ConnectionView],
  playerTransform: Transform)
```

注意 `InquiryPointView` **不下发 `Subject`、`topics`、`SceneCue`**——否则前端可以枚举出"这个物体背后挂了几个主题"，等于泄露"这里有没有秘密"。

### P0-5　3D 坐标与空间约定完全缺失（必须新增一章）

现状：`playerPosition: Vec3`、`SceneNode.transform`、`Entry.World(center)`、`Entry.OnNode.localPoint` 四个坐标并存，但**没有任何一处说明它们的坐标系关系**。服务端要校验"玩家能否触达某个入口"就必须有这条变换链。

建议新增 **§22 坐标与空间约定**，明确写下：

1. 每个 `Place` 有唯一局部原点；`SceneNode.transform` 相对其**父节点**；无父节点时相对所属 `Place` 原点。
2. `Entry.World(center)` 用 `Place` 局部坐标；`Entry.OnNode(localPoint)` 用**节点局部**坐标，世界位置 = 父链变换相乘后再乘 `localPoint`。
3. `GameState.playerPosition` 与 `PlayerMoved.position` 用 `Place` 局部坐标（与 `Entry.World` 同一空间，便于直接算距离）。
4. 坐标系：右手系、Y 向上、单位米（已在 §2 定义，需在此重申适用对象）。
5. 需要一个 `worldTransformOf(node): Transform` 的纯函数名（实现放对应服务 `utils`），并声明它是**唯一**把局部坐标转世界坐标的入口。

同时补 `SceneNode.parent`（下一节 P1-4）。

### P0-6　移动合法性：必须**明确选择**一种策略

`PlayerCommand.Move(place, position)` 注释说"仅允许开放连接和合法落点"，但"合法落点"没有类型（`Place` 没有可行走边界）。两种情况二选一，必须写死在文档里：

| 方案 | 做法 | 成本 |
|---|---|---|
| A（推荐 v1） | **信任客户端位置**，服务端只校验：`Place` 归属正确、该 `Place` 已开放、来源 `Place` 与目标 `Place` 之间有 `Connection` 且未上锁 | 低 |
| B | `Place` 增加 `walkable: NonEmptyVector[Box]`，服务端做 AABB 判定 | 中，且需与 3D 场景坐标逐一对齐 |

建议 v1 取 A，并在文档写明这是**有意的范围裁剪**，而不是"已实现物理模拟"。否则第 6 天会被问"你怎么防止穿墙"。

### P0-7　单案静态结构下，三处复杂度可以直接不用

因为 v1 是"一具尸体、侦探到场后世界静态"，以下三块在 v1 **不需要实现**（类型保留，标注"v2 使用"）：

| 类型 | v1 处理 |
|---|---|
| `ScheduledOccurrence`、`PlannedObservation` | 不使用；`initial.occurredEvents` 包含全部历史事件 |
| `AdvanceTime` / `StoryTimeAdvanced` | 不使用；`storyTime` 全程等于 `initialStoryTime` |
| 多 `Incident` 分别指控 | v1 只有一个 `Incident`，`Accusation` 一次提交即可 |

**收益**：§11（剧情推进）、§16.2 规则 5（按剧情时刻拆提交）的实现成本全部归零；`Incident` 也可以简化为单一必答集合。

`PlayerCommand.Submit(answer: Accusation)` 的单次语义在 v1 成立，**无需修改**——但要在文档注明"多 Incident 分别指控需 v2 扩展为 `Vector[Accusation]`"。

---

## 3. P1 类型补丁（v1 中必须，可否决）

### P1-1　`InquiryPoint` 缺默认回退话术

玩家会到处乱点。`AiDecision.Unspecified` / `Insufficient` 只是枚举，**给玩家看什么文字**没有着落，每次让 AI 现编"这里没什么特别的"既慢又可能泄密。

```scala
final case class InquiryPoint(
  id: PointId, subject: Subject, label: Text, entry: Entry, topics: Set[TopicId],
  fallbackFinding: Option[FindingContent]     // 新增：未匹配任何路线时的固定说法
)
```

命中 `fallbackFinding` 就直接返回，**不调用 AI**。这同时降低演示时的 API 成本与超时风险（对应 `plan.md` 风险四）。

### P1-2　`Clue` 缺"可用于质询哪些角色"

`plan.md` 5.3 明确列了"可用于质询哪些角色"，页 09 的"出示证据"UI 需要它。

```scala
final case class Clue(
  id: ClueId, title: Text, finding: Text,
  facts: NonEmptyVector[FactId], relatedClaims: Set[ClaimId],
  result: FindingContent,
  suggests: Set[PersonId]                     // 新增：建议向谁出示
)
```

### P1-3　`NpcAiContext` 缺"现在是什么时候、在哪里"

NPC 被问"你昨晚在哪"时，模型不知道"现在"是什么时候，会直接产生剧情矛盾。

```scala
final case class NpcAiContext(
  person: PersonId, profile: PersonProfile, state: NpcState,
  storyTime: CaseTime,                        // 新增
  currentPlace: PlaceId,                      // 新增
  permittedFacts: Vector[Fact], perceivedClues: Vector[KnownClue],
  privateContexts: Vector[PrivateContext], memories: Vector[NpcMemory],
  permittedClaims: Vector[Claim], recentConversation: Vector[ConversationRecord],
  disclosureRoutes: Vector[CandidateRoute]
)
```

### P1-4　`SceneNode` 缺父子层级

现在节点是扁平列表。抽屉在书桌里、画挂在墙上——移动书桌时子部件不会跟随，只能靠第 503 行"移动时须同时配置目标场景的合法节点变换"这种人工约定补。现在加成本最低：

```scala
final case class SceneNode(
  id: SceneNodeId, asset: AssetId, transform: Transform,
  parent: Option[SceneNodeId],                // 新增；须无环
  represents: Option[EntityId], place: PlaceId, initiallyVisible: Boolean
)
```

补充约束：`parent` 链不得成环；父节点与子节点必须属于同一 `Place`；`buildInitialState` 需校验。

### P1-5　人物与账户的展示字段

```scala
final case class PersonProfile(
  trueName: Text, identities: NonEmptyVector[Identity], background: Text,
  personality: Text, appearance: Text,        // 新增：3D 角色需要外观描述
  values: Vector[Text], skills: Vector[Text]
)

final case class UserProfile(
  id: UserId, nickname: Text, avatar: Option[AssetId], createdAt: Instant  // 新增 avatar
)
```

### P1-6　案件列表与（若保留）生成契约

页 02 需要列表项；页 04 若保留"生成"则需要请求类型。

```scala
enum Difficulty:
  case Easy, Normal, Hard

final case class CaseSummary(
  id: CaseId, revision: Revision, title: Text, difficulty: Difficulty,
  placeCount: Int, suspectCount: Int, estimatedMinutes: Int,
  creator: Option[UserId], publishedAt: Instant
)
```

若 v1 只做预置案件（推荐），则页 04 改为"选择案件"，**只需 `CaseSummary`，不需要 `CaseGenerationRequest`**；生成能力整体推到 v2，与 `CaseDraft` 一起设计。

---

## 4. P2：v2 预留（现在只留扩展位，不建类型）

| 项 | v1 动作 |
|---|---|
| 玩家扮演嫌疑人 | 保留 `GameMode.SuspectChallenge` 这个 case；保留 `PlayerRole.character: Option[PersonId]`。**不建** `PlayerCharacter`。 |
| 玩家陈述结构化 | **不建** `PlayerStatement`。但 `ConversationRecord` 现在是开放结构，v2 可加 `statement: Option[...]` 而不破坏旧日志。 |
| AI 侦探（对手方） | 不建。`PlayerRole` v2 再加 `counterpart`。 |
| 案件 AI 生成 | 不建 `CaseGenerationRequest` / `CaseDraft`，除非 P1-6 决定 v1 要做。 |
| 多 Incident 分别指控 | `Accusation` 单数 v1 够用；v2 扩展为 `Vector[Accusation]`。 |

**前向兼容性评估**：以上都只是"新增提交类型 / 新增可选字段"，不改已有日志格式，重放语义不受影响。风险低。

---

## 5. 建议新增的文档章节

| 章节 | 内容 |
|---|---|
| **§22 坐标与空间约定** | P0-5 的全部内容 + `worldTransformOf` 的职责声明 |
| **§23 服务归属与依赖方向** | 1.3 的归属表 + 单向依赖图 + "跨服务纯函数一律提升为 API 消息"的规定 |
| **§24 API 层契约** | 1.2 的消息清单、`apiTypes` 清单、`GameError → HttpError` 映射表、路由名推导规则（规则 31） |
| **§25 编解码策略** | 1.4 的三类模板（ID / `NonEmptyVector` / 枚举二分）+ `jsonb` 声明 |
| **§26 v1 范围与裁剪** | P0-7 的"不使用清单" + P0-6 的移动策略选择 + 页数调整说明 |
| **§27 表结构归属** | 把 §20.1 的"建议存储集合"落到 `XXTable` / `XXTableInitializer` 粒度，并明确"当前投影 vs 日志"谁是唯一真值 |

---

## 6. 变更总表

| 类型 | 动作 |
|---|---|
| 全部 20 余个 ID 值类型 + `Text` | **修改**：伴生对象补 `fromString` 非空校验、`Encoder`/`Decoder`、`KeyEncoder`/`KeyDecoder` |
| `NonEmptyVector` | **修改**：改为项目自有类型 + 伴生 codec |
| 全部带载荷枚举 | **修改**：明确走结构化 JSON，判别字段名统一 |
| `SolutionRequirement` | **修改**：加 `label: Text` |
| `RequirementResult` | **修改**：加 `incident`、`label` |
| `AccusationCandidate` / `AccusationOptions` | **新增** |
| `SceneAuthoringInput` / `SceneBounds` / `AssetLibrary` | **新增** |
| `ReviewOutcome` + `RequirementReview` | **修改**：用三态枚举替代"缺项即未检查" |
| `PlayerSceneView` / `SceneNodeView` / `InquiryPointView` / `ConnectionView` | **新增** |
| `InquiryPoint` | **修改**：加 `fallbackFinding` |
| `Clue` | **修改**：加 `suggests` |
| `NpcAiContext` | **修改**：加 `storyTime`、`currentPlace` |
| `SceneNode` | **修改**：加 `parent` |
| `PersonProfile` | **修改**：加 `appearance` |
| `UserProfile` | **修改**：加 `avatar` |
| `CaseSummary` / `Difficulty` | **新增**（页 02 必需） |
| `CaseGenerationRequest` / `CaseDraft` | **新增**，仅当 v1 保留 AI 生成 |
| `LoadCasePackageMessage` / `DeriveFactsMessage` | **新增**（API 层，规则 9/10） |
| 约 14 个 `*Message` + 对应 `apiTypes` | **新增**（API 层，规则 20/24） |
| `GameError` | **保留**，新增 `toHttpError` 映射 |
| `PlayerCommand` | **保留**为领域指令集，不再充当 API 输入 |
| `ScheduledOccurrence` / `PlannedObservation` / `AdvanceTime` | **保留，v1 不使用** |
| `PlayerCharacter` / `PlayerStatement` | **不建**，v2 再议 |
| `CaseTruth` / `Event` / `Fact` / `Person` / `SessionCommit` / `Snapshot` / `RequestReceipt` | **保持不变** |

---

## 7. 需要作者拍板的问题

1. **v1 是否保留"AI 案件生成"？** 是 → 需要 `CaseGenerationRequest` + `CaseDraft`，且必须要有一个可复用的 `SceneTemplate`；否 → 页 04 改为"选择案件"，只需 `CaseSummary`。
2. **移动校验取方案 A（信任客户端位置）还是方案 B（可行走区域）？** 建议 A。
3. **页 11/12/13 用什么补位**（嫌疑人模式降级后少三页）？候选：AI 调用监控页、场景资料库页、规则依赖图页。
4. **`objects/` 是否按域建子目录**（如 `objects/truth/`、`objects/scene/`、`objects/session/`）？130 个类型平铺会显著影响可浏览性（规则 13、56）。
5. **`services/accounts` 还是 `services/user`？** 课程样本用 `user`，若保持 `accounts` 需在文档说明理由。

## 8. 复核结论（2026-10-27，以本节补充原建议）

本节保留原评审正文用于追溯，纠正不能直接采纳的判断。用户已明确：**只借鉴人物关系与误导结构，另写现代原创案情；当前先考虑类型，案情随后设计。** 原文的单房间、只侦探模式、取消生成等前提不是全部已获确认。

### 8.1 采纳与调整

| 建议 | 复核 | 类型初稿 v1.3 的处理 |
|---|---|---|
| API／DTO、编解码和 Table 归属要补齐 | 方向正确；原文是领域草案，不能据此判定尚未实现代码已经违反课程 | 增加落地清单与边界，源码实现时逐项完成 |
| 正确答案缺标签 | 正确 | SolutionRequirement、RequirementResult 增加 question／incident／label |
| 指控选项不足 | 正确，但原方案存在泄密和真假混淆 | 新增 QuestionId、OptionId；候选是独立假设，不包含 SolutionRequirement，不把错误候选装成 Fact |
| 使用 affectedPeople 生成嫌疑候选 | 不正确 | 受影响者可能只有死者；采用作者显式候选配置 |
| 制作输入要实体化 | 正确 | 新增 SceneAuthoringInput、AssetLibrary、SceneBounds；展开 Fact 内容，不只给 ID；可选素材集合允许为空 |
| 验收 Pending 三态 | 采纳，属于有限工作流 | ReviewOutcome；缺项仍视为不完整，不能用默认 Passed 补齐 |
| 玩家视图 | 必须补 | PlayerSceneView 等安全 DTO；多 Fixed 操作可列出，不把每个入口限制成一个 operation |
| 坐标与 parent | 缺“完整”变换链，而非全部缺失 | 加 parent 和坐标章；合成矩阵用 Matrix4，避免非均匀缩放后错误拆回 TRS |
| 信任客户端位置方案 A | 不建议作为默认保证 | 使用简化 walkable AABB＋开放连接／入口落点；说明其不等于完整防穿墙 |
| 固定回退话术 | 采纳但修正触发逻辑 | 无候选／匹配失败／模型失败才回退；不能有 fallback 就不判断任何合法请求，也不能回退成“没有痕迹” |
| 线索质询建议 | 采纳为 UI 建议 | suggests 不限制自由出示，且过滤未公开人物关系 |
| NPC 当前时刻和地点 | 采纳 | 增加 storyTime、日历原点、时间原点说明与 currentPlace |
| NPC 外观 | 采纳 | PersonProfile.appearance，公开投影仍独立审核 |
| 账户头像、难度、生成契约 | 非当前类型 P0 | 列表先有最小 CaseSummary，不为未确认功能扩张依赖 |
| 静态单案配置 | 可作实施建议，不强制裁剪通用类型 | future 计划可为空；认知日志继续变化，多 Incident 也可逐个提交 |
| 单一独立代码目录按域分类 | 合理 | 同服务 objects 下按清晰子域分类；不因样本叫 user 而必须把 accounts 重命名 |

### 8.2 不能照抄的技术细节

1. **候选响应泄密。** 原 AccusationOptions.requirements 包含正确 conclusion 和 acceptedClueSets，必须删除。正常的“A 是凶手”候选并不自动泄密，问题在于暴露哪个选项正确或仅围绕正确答案生成选项。
2. **错误候选不能是 Fact。** Fact 是真相记录；加入错误事实以便构造选项会污染证据链。现用独立 OptionId 与 hypothesis，正确映射只在服务端。
3. **依赖表原案本身有循环。** “全部 ID 属 cases”与“SessionId 等属 games”重复；cases 中 PersonPerspective 等又引用 RecordId，且初始观察等固定配置若属 games，会反向依赖。v1.3 将通用运行标识归 system、固定配置归 cases，当前状态／日志归 games。
4. **跨服务 API 规则只限制公共边界，不要求纯函数调用 IO。** cases 的 validateCase 直接调用本服务私有纯推导，games 经公开消息调用；批准推导结果记入日志，重放不依赖 IO。不能在纯 validateCase 中塞 `.plan(connection)`。
5. **不增加只转发的读取消息。** CaseRevisionTable 已提供公开 IO 读取时，games 可直接调用；课程规则允许，无需必建 LoadCasePackageMessage。
6. **样本不能不经改动长事务调用模型。** 已核对 APIMessageRouter：它将 plan 包入数据库事务。AI 审讯需短事务／网络调用／短事务的基础设施支持，不能持锁等模型，也不能以释放了的连接继续查询。该项是比外观字段更高优先级的落地问题。
7. **消息示例缺 RequestId。** 去重契约不能在拆 API 后丢掉。APIWithTokenMessage 在样本中是标记，真正的 token→userId 转换在路由，不能只继承标记就算授权。
8. **样本路由名推导不等于提案口述规则。** 已核对 APIMessage.scala，采用去除 APIMessage 后追加 API 再小写；实施时统一前后端算法，不能把未匹配命名的 Message 自动当成想象路由。
9. **HttpError 工厂不全。** 样本仅 BadRequest／Unauthorized／Forbidden／NotFound／Conflict；503、422 需要扩展异常和 router，不调用不存在的工厂。基础错误映射函数不是一个业务 API 消息。
10. **Map key codec 缺口成立，给出的模板仍需修。** 采用验证型 KeyDecoder.instance 是清楚的写法；不能未经当前 Circe 版本核对使用 decodeKeyString.emap。ID 不应被静默 trim 后碰撞。
11. **混合 ADT 整体编码。** Fixed(label) 本身有载荷；Observer／Actor 等不能按不同 case 混用字符串和 object。普通 deriveEncoder 不自动得到 kind 判别字段，需显式选择配置／实现并往返验证。
12. **jsonb 与强类型不冲突。** 存类型化 JSON 可以；将记录改成另表可独立更新而日志只留引用则会破坏重放。本版保留自足提交，其他记录表作索引投影。
13. **“只加可选字段所以不破坏重放”不成立。** 旧解码器、默认值、枚举 case 和 reducer 行为都需版本策略。多 Incident 逐个提交也可实现，不必强制 Vector[Accusation]。

### 8.3 额外补充的类型与内容边界

- 首版常用现代刑侦能力不等于所有询问都能自动形成结论；但也不能为了保留诡计而无依据拒绝合理检验。正式案情编写时需要审核检测结果、可获得条件和证据链，当前不先编案件。
- 关系案更适合首版控制制作成本；特殊手法仍可用固定 Event／Fact、检验报告、预制演示和 Operation 表达。只有要求玩家自由改装并计算不同后果时，才必须新增专门仿真模块。
- “一具尸体／单现场／静态世界”是拟采用的 Demo 配置，不应被写成参考原著的客观性质。新案参考文件中的回忆级细节存在错误，未核对条目不进入真相。本轮按用户要求不继续制定案情。
- 原著细节纠错的来源已核查：[作者官网作品页](https://www.agathachristie.com/en/stories/the-murder-at-the-vicarage)、[原著第30—32章](https://www.gutenberg.org/files/78220/78220-h/78220-h.htm)。此来源只用于确认参考材料不能照搬，现代原创案不继承原著凶手与具体手法。

### 8.4 路径与验证说明

本文开头 `../../../plan.md` 实际不指向项目规划，应为 `../../plan.md`。课程规则和 sample 路径则是相对工作区的有效路径。

本轮主要核对来源：本地 rules.txt、sample 的 APIMessage.scala／APIMessageRouter.scala／HttpError.scala／build.sbt，以及两个输入文档。类型文件中的新增代码仍为契约；未生成源码、安装依赖、运行数据库或声称模板已编译。
