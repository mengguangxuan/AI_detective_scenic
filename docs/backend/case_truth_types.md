# 后端领域类型完整初稿 v1.4

本版在 v1.2 上吸收经复核的落地建议，补充安全候选、空间层级、玩家投影与编解码／模块边界。首版案件方向改为现代原创关系案；具体案情随后设计，不在本版裁定角色或手法。侦探默认现代常用刑侦能力的约定继续有效。文档是待评审的领域数据契约，尚未生成 Scala 源码、数据库表或 API。已有 API／消息／玩家响应草案已分离至 [api_design_draft.md](api_design_draft.md)，当前优先讨论领域类型。课程规范为相对本文目录的 `../../../samples/samples/rules.txt`。

**核心原则：开放语义记录案件内容，强类型固定对象关联与执行边界。** 场景提示异常，检查确认信息；人物有自己的故事版本；AI 不创造客观真相。画面没表现不代表不存在。

## 1. 范围、模块和阅读顺序

| 数据组 | 核心类型 | 是否可在本局修改 |
|---|---|---|
| 语义世界 | WorldSpec、Place、Entity、SemanticScene | 定稿内容不可改；本局位置与开合另存 |
| 客观案件 | CaseTruth、Event、Fact、PrivateContext、Incident | 不可改；未来事件是否已发生另存 |
| 人物视角 | Person.initialPerspective、BelievedEvent | 开局版本不可改；当前视角可修订 |
| 公开资料 | PublicBackground、PublicPersonProfile、InitialReport | 定稿内容不可改；本局是否取得另存 |
| 场景表现 | SceneSpec、SceneNode、InquiryPoint | 资产与绑定不可改；节点变换另存 |
| 调查规则 | DiscoveryRoute、Operation、DeductionRule | 不可改；完成与发现状态另存 |
| 会话与变化 | GameSession、GameState、SessionCommit、Snapshot | 按事务追加变化，更新当前投影 |

建议先看第 3 节的时间、第 5 节的真实／主观事件、第 6 节的人物视角，再看运行日志。不是每个类都对应一张表，也不是每次查询都加载整份聚合。

建议后端归属：`services/accounts/` 管身份，`services/cases/` 管定稿内容与发布校验，`services/games/` 管会话、调查、NPC 当前视角与结案；`services/system/` 管连接、事务、模型客户端等基础设施。每个业务模块按课程要求组织 api、objects、routes、tables、utils，不为每个领域名词单独创建微服务。

## 2. 通用类型和所有 ID

所有编号是独立类型，底层字符串只用于查找，不解析其拼写执行行为。实现时一类型一文件，同名伴生对象放编解码器；下文集中展示仅为便于阅读。

```scala
final case class CaseId(value: String)
final case class UserId(value: String)
final case class SessionId(value: String)
final case class RequestId(value: String)
final case class PlaceId(value: String)
final case class EntityId(value: String)
final case class PersonId(value: String)
final case class EventId(value: String)
final case class BelievedEventId(value: String)
final case class PrivateContextId(value: String)
final case class IncidentId(value: String)
final case class QuestionId(value: String)
final case class OptionId(value: String)
final case class MotiveId(value: String)
final case class TopicId(value: String)
final case class FactId(value: String)
final case class ClaimId(value: String)
final case class ClueId(value: String)
final case class ReportId(value: String)
final case class PointId(value: String)
final case class RouteId(value: String)
final case class OperationId(value: String)
final case class RuleId(value: String)
final case class AssetId(value: String)
final case class SceneNodeId(value: String)
final case class SceneRequirementId(value: String)
final case class SourceId(value: String)
final case class RecordId(value: String)
final case class AiCallId(value: String)
final case class Text(value: String)
final case class Revision(value: Int)
final case class Sequence(value: Long)
final case class PerspectiveRevision(value: Long)
final case class CaseTime(seconds: Long)
final case class TimeWindow(earliest: CaseTime, latest: CaseTime)
final case class Vec3(x: Double, y: Double, z: Double)
final case class Rotation(xRad: Double, yRad: Double, zRad: Double)
final case class Transform(position: Vec3, rotation: Rotation, scale: Vec3)
final case class Matrix4(values: Vector[Double])
final case class SceneBounds(min: Vec3, max: Vec3)
final case class MovementPolicy(maximumStepMeters: Double)
```

- ID、Text 非空；Revision > 0；Sequence、PerspectiveRevision 从 0 开始递增。`NonEmptyVector[A]` 为经过校验的非空有序集合，采用项目统一的集合实现。
- `Instant` 为系统 UTC 时间；`OffsetDateTime` 为可选的公历原点；`URI` 为登记资源地址，均使用标准库类型。
- CaseTime 是相对剧情原点的秒数，允许负值；TimeWindow 为闭区间，earliest ≤ latest。精确时刻用两端相同值。不补造原作未给出的精确时刻。
- Vec3 单位米，右手系、Y 向上；Rotation 为 XYZ 弧度、按 X→Y→Z 应用；scale 分量 > 0；所有数值须有限。
- Option 表示不适用或未提供引用，不承担“确认不存在”的语义。缺失事实使用 Existence.Absent。
- EventId 指向 CaseTruth.events 中的 Event；BelievedEventId 指向某人当前视角中的 BelievedEvent，两者不能互换。后者在 `(PersonId, PerspectiveRevision)` 内唯一。
- RecordId 指向本局日志中的检查、对话、获知、认知变化或指控记录；所有此类记录 ID 在同一会话内唯一。RequestId 指向一次外部请求，Sequence 标识一次原子提交的顺序。
- 所有案件引用限定同一 `(CaseId, Revision)`；发布修订不可原地修改，会话固定绑定修订。禁止 Any、任意 JSON 参数袋和可执行脚本字符串。

## 3. 三条时间线和一个提交顺序

| 时间 | 放在哪里 | 含义 |
|---|---|---|
| 事情发生时间 | Event.account.when | 人或物实际何时经历事件 |
| 人物理解的发生时间 | BelievedEvent.account.when | 可能与实际不同，不自动纠正 |
| 本局获知／调查时间 | SessionCommit.meta.storyTimeAfter | 玩家和 NPC 在什么剧情时刻获得信息 |
| 系统记录时间 | SessionCommit.meta.recordedAt | 现实何时保存，不用于案件时间推理 |
| 提交顺序 | SessionCommit.meta.sequence | 同一时刻也能确定先后、重放和去重 |

例如：死亡发生在昨晚20:40；弟弟今天10:00才看到死亡时间报告；系统今天14:32保存了该次调查。不能把这三个时间合成一个 timestamp。

CasePackage.initialStoryTime 是开局剧情时刻；CaseTruth 可以包含更早历史和预先写好的后续事件。GameState.storyTime 只经已登记的 AdvanceTime 操作推进，不因模型回复慢、暂停或真实时间流逝而自动推进。序号负责并发和日志顺序，剧情时间负责叙事。

日志元数据集中在提交中，记录通过序号关联，不给每个领域对象复制三套时间字段：

```scala
enum Actor:
  case Player
  case Npc(person: PersonId)
  case System
final case class CommitMeta(
  session: SessionId, sequence: Sequence, request: RequestId,
  actor: Actor, storyTimeBefore: CaseTime, storyTimeAfter: CaseTime,
  recordedAt: Instant
)
```

Actor 是提交身份协议，不是穷举故事角色；具体人物身份仍使用语义档案。请求日志记录可信认证结果，不能相信客户端自报的 actor。

## 4. 语义世界与场景制作输入

```scala
final case class GeoPosition(latitude: Double, longitude: Double)
final case class Place(
  id: PlaceId, name: Text, parent: Option[PlaceId],
  geography: Option[GeoPosition], background: Text,
  walkable: Vector[SceneBounds]
)
final case class Connection(
  from: PlaceId, to: PlaceId, bidirectional: Boolean,
  minimumTravelSeconds: Int, gate: Option[EntityId],
  fromEntry: Vec3, toEntry: Vec3
)
final case class Environment(
  place: PlaceId, during: TimeWindow, weather: Option[Text],
  temperatureC: Option[Double], electricity: Option[Boolean], description: Text
)
final case class Entity(
  id: EntityId, name: Text, initialPlace: PlaceId,
  owner: Option[PersonId], background: Text
)
final case class Relationship(
  from: PersonId, to: PersonId, relation: Text,
  sources: NonEmptyVector[SourceId]
)
final case class SemanticScene(
  place: PlaceId, overview: Text, atmosphere: Text,
  focalEntities: Vector[EntityId], decorativeNotes: Vector[Text],
  requirements: Vector[SceneRequirement],
  sources: NonEmptyVector[SourceId]
)
final case class SceneRequirement(
  id: SceneRequirementId, entities: NonEmptyVector[EntityId],
  requirement: Text, relatedFacts: Set[FactId], relatedPoints: Set[PointId],
  appliesDuring: Option[TimeWindow], acceptance: NonEmptyVector[Text],
  sources: NonEmptyVector[SourceId]
)
final case class WorldSpec(
  places: Vector[Place], connections: Vector[Connection],
  environments: Vector[Environment], entities: Vector[Entity],
  relationships: Vector[Relationship], scenes: Vector[SemanticScene]
)
```

Place 包含关系无环，经纬度合法；Connection 的耗时非负。Environment 描述实际适用时段，同地点冲突时段须拆分，不靠遍历顺序取值。`electricity=None` 是未设定，不是断电。特殊天气、潮汐、血迹冲刷等继续用语义和 Fact 表达，不枚举所有自然现象。

Entity.initialPlace 是开局位置，当前实体位置在 GameState.world.entityPlaces 中；人物身体由 Person.body 绑定，人物当前地点也从该唯一来源读取。

SemanticScene 是制作说明，不是另一套事实数据库。decorativeNotes 只放允许 AI 补充的木纹、灰尘、装饰配色等美术说明；关键布置与表现必须放 requirements，不能混在一段自由备注中。影响推理的要求必须有 relatedFacts，允许尚未涉及推理的必要建模要求仅关联实体；所有要求均须验收。

SceneRequirement 是此前 SpatialRequirement 想法的通用版本：除了“钟在书桌上方”，还可表达“钟面显示特定时间”“门缝足够明显但不能暴露暗室全貌”等案件要求，不枚举全部空间关系或表现类型。entities 精确关联对象，relatedFacts 说明依据，relatedPoints 关联调查入口，acceptance 说明怎样验证完成。每条要求只在所属 SemanticScene 内定义，ID 在整个案件修订内唯一。

appliesDuring=None 表示本案件场景持续适用；有值表示只针对该剧情时段。它用于制作与检查阶段区分场景状态，不会自动生成运行时变换或动画。需要随进度变化时，必须配置已有 Operation／ScheduledOccurrence 的 WorldEffect 并分别验收相关状态。

AI 在制作阶段根据语义世界、明确要求、获准线索提示和已实现交互能力写代码／制作资产，预览审核后登记资源。案件配置没提供的关键细节不能自行补成事实；要求互相矛盾或缺少事实依据时报告问题，不临场改真相。世界说明可能含隐藏地点或关系，不默认公开。

## 5. 真实过程、背景、动机与责任

真实和主观事件共用内容字段；身份、因果和结果引用分开。

```scala
final case class EventAccount(
  when: Option[TimeWindow], actors: Set[PersonId],
  targets: Set[EntityId], places: Vector[PlaceId], tools: Set[EntityId],
  background: Text, process: Text, outcome: Text
)
final case class Event(
  id: EventId, account: EventAccount, causedBy: Set[EventId],
  resultingFacts: Set[FactId], sources: NonEmptyVector[SourceId]
)
final case class Motive(
  id: MotiveId, owner: PersonId, explanation: Text,
  targets: Set[PersonId], triggerFacts: Set[FactId], goal: Text
)
final case class Responsibility(
  person: PersonId, contribution: Text, intention: Text,
  motives: Set[MotiveId], preparation: Vector[EventId],
  execution: NonEmptyVector[EventId], concealment: Vector[EventId]
)
final case class Resolution(
  responsibilities: Vector[Responsibility],
  causeFacts: NonEmptyVector[FactId], explanation: Text
)
final case class Incident(
  id: IncidentId, title: Text, question: Text,
  affectedPeople: Set[PersonId], affectedEntities: Set[EntityId],
  events: NonEmptyVector[EventId], resolution: Resolution
)
```

EventAccount 依次回答“何时、谁、涉及什么、在哪里、用什么、什么前情、怎样发生、造成什么结果”。擦拭门把手、误导警方、替换药物等都写入 process，不创建 EventKind。

真实 Event 要求 when 有值、places 非空；主观事件允许不知道时间和地点。字段集合不完整不能自动证明“没有其他行为人”。真实与主观两个因果图分别无环；真实 causedBy 不允许时间倒置，不确定区间不能被当作精确不在场证明。

整案 CaseTruth.background、事件 account.background、人物想象的 background 各自独立。真相依据经过审核的 Event 和 Fact，Event 不是物理回放脚本；无需计算每滴血或模拟全部动作。resultingFacts 是认可结果的引用，outcome 是该版本的语义说明。

责任角色、意图、动机不强制枚举。Resolution.responsibilities 可以为空，表达意外、自然原因、误会；也可以含多人和复合贡献。姓名、身份、动机与手法通过 Person、Motive、Event 的引用组成，不另存重复“凶手档案”。

## 6. 事实、私人内情与对外说法

```scala
enum Subject:
  case Place(id: PlaceId)
  case Entity(id: EntityId)
  case Person(id: PersonId)
  case Event(id: EventId)
  case Incident(id: IncidentId)
  case Clues(ids: NonEmptyVector[ClueId])
final case class Topic(
  id: TopicId, subject: Subject, question: Text,
  when: TimeWindow, boundary: Text
)
enum Existence:
  case Present, Absent, Unspecified
enum FactValue:
  case ExistenceState(value: Existence)
  case Quantity(value: Double, unit: Text)
  case Time(value: TimeWindow)
  case Relation(left: Subject, relation: Text, right: Subject)
  case AuthoredDetail(detail: Text)
final case class Fact(
  id: FactId, topic: TopicId, value: FactValue,
  requiresOccurred: Set[EventId], explanation: Text,
  sources: NonEmptyVector[SourceId]
)
final case class PrivateContext(
  id: PrivateContextId, involvedPeople: Set[PersonId], background: Text,
  facts: NonEmptyVector[FactId], initiallyKnownBy: Set[PersonId]
)
final case class Claim(
  id: ClaimId, speaker: PersonId, topic: TopicId,
  claimedValue: FactValue, wording: Text, intention: Text
)
```

- 单个 Topic 对应一个 Fact；范围／时间不同建立新 Topic。boundary 是语义范围，调查入口负责实际访问限制，不解析文字做精确几何查询。
- 存在、不存在、未设定严格分开。未设定不得用于排除嫌疑、成为线索、推导结论；查询不到主题也不等于不存在。
- requiresOccurred 限定某事实依赖的真实事件必须已在本局发生，即使玩家伪造 TopicId 也不能查询未来事实。它不是“知道这些事件才能看”的条件；发生与获知分开。
- AuthoredDetail 正常承载开放事实，不意味着程序能验证全部语义。程序通过 FactId 绑定固定内容；数值单位不自动做自由文本换算。
- PrivateContext 是真实内情，involvedPeople 是涉及者，initiallyKnownBy 是开局知情者；同一内情若只知部分应拆记录。引用该内情的视角须知道其全部 facts；误会的私人关系写主观故事，不伪造真实关系。
- Claim 是已编写的说法，可真可假；intention 可表达误信、回避、保护、误导，不枚举所有心理。玩家在本局实际听到它时，另写 ConversationRecord。预设 Claim 存在不代表已被说出。

## 7. 人物自己的版本与获知依据

```scala
final case class Identity(name: Text, occupation: Option[Text])
final case class PersonProfile(
  trueName: Text, identities: NonEmptyVector[Identity], background: Text,
  personality: Text, appearance: Text, values: Vector[Text], skills: Vector[Text]
)
final case class KnownClue(
  clue: ClueId, noticed: Text, interpretation: Text,
  confirmedFacts: Set[FactId]
)
enum InformationSource:
  case HistoricalEvent(event: EventId)
  case InitialReport(report: ReportId)
  case Clue(clue: ClueId)
  case PrivateContext(context: PrivateContextId)
  case Claim(claim: ClaimId)
  case SessionRecord(record: RecordId)
  case Background(source: SourceId)
final case class InitialAwareness(
  when: TimeWindow, sources: NonEmptyVector[InformationSource],
  facts: Set[FactId], clues: Set[ClueId], contexts: Set[PrivateContextId],
  heardClaims: Set[ClaimId], account: Text
)
final case class BeliefBasis(
  clues: Set[ClueId], privateContexts: Set[PrivateContextId],
  heardClaims: Set[ClaimId], priorBeliefs: Set[BelievedEventId],
  records: Set[RecordId], reasoning: Text
)
final case class BelievedEvent(
  id: BelievedEventId, account: EventAccount,
  causedBy: Set[BelievedEventId], basis: BeliefBasis, confidence: Text
)
final case class PersonPerspective(
  privateContexts: Set[PrivateContextId], relatedClues: Vector[KnownClue],
  knownFacts: Set[FactId], heardClaims: Set[ClaimId],
  believedEvents: Vector[BelievedEvent], story: Text,
  intentions: Vector[Text], disclosureStrategy: Text
)
final case class Person(
  id: PersonId, body: EntityId, profile: PersonProfile,
  initialPerspective: PersonPerspective, initialAwareness: Vector[InitialAwareness]
)
```

**真实过程、相信的经过、说出口的版本是三件事。** BelievedEvent 可以想象根本没发生的谋杀，不能强制对应 EventId；它的 causedBy、priorBeliefs 只引用本人该视角版本中的 BelievedEventId，不得成环。confidence 用语义表达坚信、动摇等，不伪装成可精确计算的概率。

KnownClue 区分见到什么与如何解读。看到袖口红色不等于知道是颜料，不能自动将全量 Clue.facts 交给 NPC；confirmedFacts 只能是实际确认的部分，且须在该人 knownFacts 中。NPC 输入不自动展开完整线索检验结果、真相事件或最终答案。

initialAwareness 解释开局信息从哪来、何时获知；必须覆盖初始 perspective 的已知事实／线索／内情／听闻，记录不晚于开局时间，HistoricalEvent 来源须已发生。SessionRecord 不允许用于初始记录。相信的事件发生时间不等于开始相信的时间；后者由初始获知范围与本局 PerspectiveChange 界定，不能从 account.when 推出。

新说法先成为听闻，不因“有人说了”自动成为 knownFacts。人物自己的推测只更新 believedEvents；既定真事实才允许进入 knownFacts。人物可以获知反证而仍拒绝接受，修订理由需与其性格、依据和故事一致。

## 8. 公开背景、公开人物与初始报告

```scala
final case class PublicPersonProfile(
  person: PersonId, displayName: Text, publicIdentity: Text,
  appearance: Text, background: Text
)
final case class InitialReport(
  id: ReportId, title: Text, authoredAt: TimeWindow,
  claimedEventTime: Option[TimeWindow], author: Text, content: Text,
  claims: Set[ClaimId], verifiedFacts: Set[FactId], media: Vector[AssetId]
)
final case class PublicBackground(
  introduction: Text, commonKnowledge: Set[FactId],
  persons: Vector[PublicPersonProfile], openingReports: Set[ReportId],
  openingMaterials: Set[AssetId]
)
final case class PublicSpec(
  background: PublicBackground, reports: Vector[InitialReport]
)
```

PublicBackground 是对外叙事，不从 CaseTruth.background 截取。PublicPersonProfile 可使用别名和公开职业，不暴露 trueName、完整身份或私人关系；固定真相里的 PersonProfile 不是前端 DTO。

报告的 authoredAt 是形成时间，claimedEventTime 是其声称的案发时间，可能与真实 Event 不同。初步验尸意见先作为 Claim 保存；经审核且确实成立的部分才进入 verifiedFacts。报告的 content 可以混合陈述与正确背景，但程序不把整段文字当成事实。

公开背景对调查方公开，不代表每个 NPC 自动全知。commonKnowledge 是可供获准角色使用的真事实清单；角色实际掌握范围仍由 initialPerspective／获知记录确定。openingReports 只向玩家开放报告，不自动让所有 NPC 读过；NPC 的 InitialAwareness 可单独引用其确实读过的报告。

后续取得报告由日志与 GameState.knowledge.availableReports 记录；报告陈述被推翻时保留原件，新增事实和解释，不原地改写旧报告。修改公开称呼也只更新本局已公开信息，不改真实身份。

## 9. 场景表现与调查入口

```scala
enum AssetKind:
  case Model, Image, Audio, Material
final case class Asset(id: AssetId, kind: AssetKind, uri: URI)
final case class SceneNode(
  id: SceneNodeId, asset: AssetId, transform: Transform,
  parent: Option[SceneNodeId], represents: Option[EntityId], place: PlaceId, initiallyVisible: Boolean
)
final case class SceneCue(
  point: PointId, authoringHint: Text, attachedNodes: Set[SceneNodeId]
)
enum Entry:
  case World(place: PlaceId, center: Vec3, radiusMeters: Double)
  case OnNode(node: SceneNodeId, localPoint: Vec3, radiusMeters: Double)
  case Material(asset: AssetId)
  case Report(report: ReportId)
  case Conversation(person: PersonId)
  case Notebook(clues: NonEmptyVector[ClueId])
final case class InquiryPoint(
  id: PointId, subject: Subject, label: Text,
  entry: Entry, topics: Set[TopicId], fallbackFinding: Option[FindingContent]
)
final case class SceneSpec(
  nodes: Vector[SceneNode], cues: Vector[SceneCue], points: Vector[InquiryPoint],
  requirementBindings: Vector[SceneRequirementBinding], movement: MovementPolicy
)
final case class SceneRequirementBinding(
  requirement: SceneRequirementId, nodes: NonEmptyVector[SceneNodeId]
)
enum ReviewOutcome:
  case Pending, Passed, Failed
final case class RequirementReview(
  requirement: SceneRequirementId, outcome: ReviewOutcome,
  method: Text, evidence: Vector[AssetId], notes: Text
)
final case class SceneReview(
  caseId: CaseId, caseRevision: Revision, sceneFingerprint: Text,
  checks: Vector[RequirementReview], reviewer: Text, reviewedAt: Instant
)
```

模型是表现，Entity 是语义实体，InquiryPoint 是调查入口；三者显式引用。关键实体可以有多个模型部件、多个入口；装饰节点可以不绑定 Entity；线索也可以没有模型、从对话或材料取得。

OnNode 入口还要求节点及全部祖先当前有效可见；隐藏父级不能通过直接提交 PointId 绕过。可见不等于无网格遮挡。容器外壳保持可见，只隐藏内容子树，避免关闭抽屉时连开抽屉入口也消失。报告／材料／对话／日志按各自访问规则判断，不统一套用空间半径。

OnNode 位置按父链合成矩阵乘 localPoint 求所属 Place 坐标；World 为地点坐标；半径 > 0，后端按接受的玩家位置、开放地点及入口条件验证访问；细粒度网格遮挡由客户端场景碰撞处理，不宣称后端已实现权威网格射线检测。人物访问检查使用其身体实体在本局的当前地点；材料只在已经取得相应资源后开放；Report 只在 availableReports 已包含该报告且报告已形成时开放；Notebook 要求相关线索已知。

SceneCue 只用于制作异常引导，不枚举全部异常。画框稍歪、色差、缺失区域均可由 authoringHint 描述，制作后的节点或资源体现。未画血滴、指纹或细微损坏，不能改变 Fact。可直接使用检查特写，无需模拟翻转、液体或痕迹生成。

运行时仅下发当前可见节点、已获材料和可操作入口的安全投影，不能整包下发隐藏资源、真实事件和主观秘密。资源 URI 必须来自允许的资产目录，节点资产类型必须为 Model；其他媒体按类型加载。

### 9.1 关键细节的制作与验收流程

1. **案件编写阶段提供要求。** 将“挂钟在书桌上方”写成 SceneRequirement，引用钟、书桌以及相关 Fact；未知或纯美术细节不据此创造推理依据。
2. **制作 AI 读取完整输入。** 对应 SemanticScene、引用实体的语义资料、这些要求所需的 Fact、SceneCue，以及当前引擎支持的操作能力共同构成制作输入。不是把整案秘密无差别交给每个角色模型。
3. **生成节点与绑定。** 生成或修改模型／代码，登记 Asset、SceneNode，再填写 SceneRequirementBinding，明确这条要求落实到哪些节点。关键部件需有可寻址节点；不能只交付一张无法关联调查入口的整屋背景。
4. **结构检查。** 所有引用存在；绑定节点归属正确地点；关键实体可通过 represents 对应；relatedPoints 的 World／OnNode 入口与实际布置一致；素材类型、变换和时段配置合法。
5. **逐条验收。** 对 acceptance 检查构图、视线、显示内容及必要交互。SceneReview 每条要求须有且仅有一项当前结论，使用 Pending／Passed／Failed；新增要求自动产生 Pending 检查项。缺项仍视为数据不完整，不能当作通过；有 Pending、Failed 或缺项均不发布场景。
6. **修改后重验。** sceneFingerprint 由节点、入口、绑定、要求、相关状态效果及制作代码／资产内容的固定清单计算摘要，不能只哈希资源 URI。改变相关输入会使旧 SceneReview 失效。复查结果属于制作／发布记录，不是玩家发现的线索。

requirement／acceptance 是作者与制作 AI 理解的语义要求，不会被运行时解析成 Above／Behind 等不断扩张的关系枚举。明确数值或遮挡测试可以在制作工具中编写专项检查，但其余要求需预览／截图／人工或 AI 辅助复核；不能仅因为两个节点中心满足 y 大小就宣称“抬头能看清”。若一个要求绑定多个部件，审核整个组合，不只看其中一个节点。

### 9.2 示例：挂钟在书桌上方

```text
SceneRequirementId：clock_above_desk
所属 SemanticScene.place：study
entities：{EntityId(clock_01), EntityId(desk_01)}
requirement：挂钟安装在书桌正上方墙面，坐在桌前抬头可读钟面；
             不能被画框或灯具遮挡。
relatedFacts：{FactId(clock_visible_from_desk)}
relatedPoints：{PointId(inspect_clock)}
appliesDuring：无（本次调查场景持续适用）
acceptance：
  - 钟和书桌分别有可绑定节点，位于同一书房。
  - 从书桌前约定的观察位抬头，可以辨认钟面。
  - 从通常调查路线能够到达并触发 inspect_clock。
sources：{已审核案件设定的 SourceId}

生成后的绑定：
  SceneNode(node_clock).represents = EntityId(clock_01)
  SceneNode(node_desk).represents = EntityId(desk_01)
  SceneRequirementBinding(clock_above_desk) = {node_clock, node_desk}
  InquiryPoint(inspect_clock).entry = OnNode(node_clock, 钟面局部位置, 交互半径)
```

坐标由制作 AI 根据房间尺度确定并验收，不要求案件作者提前写出每个坐标。若案件要求钟面显示21:10，再增加一条引用时间 Fact 的 SceneRequirement；不能让 AI 随意画成10:10。若所用模型不支持转动指针，可制作固定钟面材质或检查特写，明确验收表现方案；不能把不支持的动画当作已实现。

与案件无关的木纹、墙漆、普通灰尘允许 AI 自行补充；任何补充不得与已知事实冲突、遮挡关键入口、泄露秘密或暗示未设定的证据。挂钟是调查入口，画面只需足够引导；涉及时间推理的准确时间仍由合法检查确认，不能以简化画面细节当作另一份真相。

## 10. 线索、可执行条件与调查途径

```scala
final case class FindingContent(text: Text, media: Vector[AssetId])
final case class Clue(
  id: ClueId, title: Text, finding: Text,
  facts: NonEmptyVector[FactId], relatedClaims: Set[ClaimId],
  result: FindingContent, suggests: Set[PersonId]
)
enum Condition:
  case All(items: Vector[Condition])
  case Any(items: NonEmptyVector[Condition])
  case ClueKnown(clue: ClueId)
  case FactKnown(fact: FactId)
  case OperationDone(operation: OperationId)
  case EventOccurred(event: EventId)
  case TimeReached(at: CaseTime)
  case PresentedTo(person: PersonId, clue: ClueId)
enum DiscoveryMethod:
  case Fixed(label: Text)
  case Semantic(
    intent: Text, acceptableApproaches: NonEmptyVector[Text],
    insufficientApproaches: Vector[Text]
  )
final case class DiscoveryRoute(
  id: RouteId, point: PointId, condition: Condition,
  method: DiscoveryMethod, grants: Vector[ClueId],
  reports: Set[ReportId]
)
```

每条路线的 grants 与 reports 至少一项非空，允许“取得报告但尚未发现完整线索”。reports 仅授予报告访问权；ReadReport 要求路线 point 的 Entry.Report 与请求报告一致、已具访问权且硬条件成立，随后将该报告审核过的 verifiedFacts 和 claims 作为本次披露包记录。若只是部分阅读，使用独立线索／Semantic 路线配置，不运行整份 ReadReport。

线索不强制归类为观察、缺失、记录等；finding 用语义说明。facts 是该次完整检查能确认的事实，不能含 Unspecified；如果声称缺失，须有相应 Absent 事实。人物只看过一部分线索时用 KnownClue 描述，不自动知道完整 facts。

All(empty)=true，Any 非空；硬条件和入口访问必须由后端校验，AI 无权忽略。Semantic 只解释玩家的自由行为是否匹配已有途径，接受方式是语义指导，不是“必须命中某个词”。关键线索至少有一条可达 Fixed 路线，以防模型超时或判断不稳。

所有候选路线还要检查其 grants／reports 涉及事实的 requiresOccurred，避免剧情未到就检查出未来结果。没有匹配路线，允许回答“未设定可确认结果”；不自动生成线索，也不从简化画面否定。

### 10.1 默认刑侦能力（用户已确认）

侦探默认可以提出并使用现代常用刑侦技术所允许的检查，不设置工具背包、工具收集或工具持有门槛。玩家可以直接提出指纹／DNA 检验、毒物检测、痕迹比对等调查请求，无需先获得放大镜、试剂、采样套件或实验设备。这里是游戏能力约定，不是穷举现实技术的目录。

- 发现方式仍以开放语义写在 Semantic 路线中，AI 将自由请求匹配到合理的既有结果；不新增 ForensicTechnique 枚举，不把工具门槛换成另一套必须收集的能力标签。
- 现场／样本／材料必须实际可访问；不能远程检验尚未发现的隐藏物体，也不能据此获知未来事件。必要的前置观察、先开门、时间和人物披露条件仍由既有 Condition 和入口规则控制。
- 检查方法可行不代表案件已经定义其结果。没有相关主题或 Fact 为 Unspecified 时返回未设定，不临时生成 DNA、指纹、实验报告或新的排除结论。
- 检查结果来自审核后的固定事实和线索，不声称运行物理／化学实验。检测时间、样本消耗和实验室排队首版不模拟；如某案件确实依赖这些条件，再显式配置相关剧情时间与事实，不默认加等待门槛。
- 真实案件 EventAccount.tools 继续保存行为人实际使用的工具／凶器，引用 EntityId；这些是历史事实与证据对象，不是侦探必须收集的工具系统。例如凶手用过某把钥匙仍可成为案件事实，但侦探检查门锁不要求先把钥匙放进背包。

## 11. 基础操作、剧情推进与预设世界变化

```scala
enum WorldEffect:
  case Open(entity: EntityId)
  case Unlock(entity: EntityId)
  case SetEntityPlace(entity: EntityId, place: PlaceId)
  case SetNodeTransform(node: SceneNodeId, value: Transform)
  case SetNodeVisible(node: SceneNodeId, visible: Boolean)
enum OperationEffect:
  case World(effect: WorldEffect)
  case Present(person: PersonId, clue: ClueId)
  case AdvanceTime(to: CaseTime)
final case class Operation(
  id: OperationId, point: PointId, label: Text,
  condition: Condition, effects: NonEmptyVector[OperationEffect]
)
enum Observer:
  case Player
  case Npc(person: PersonId)
final case class LearnedInformation(
  facts: Set[FactId], clues: Vector[KnownClue],
  privateContexts: Set[PrivateContextId], heardClaims: Set[ClaimId],
  reports: Set[ReportId]
)
final case class PlannedObservation(
  recipient: Observer, information: LearnedInformation, account: Text
)
final case class ScheduledOccurrence(
  event: EventId, at: CaseTime, worldEffects: Vector[WorldEffect],
  observations: Vector[PlannedObservation]
)
```

Operation.effects 按声明顺序规划和应用；AdvanceTime 在当前位置展开途中事件，后续效果在推进后的时刻生效。整个请求先验证完整计划，失败不提交任何部分变化；OperationCompleted 在全部效果成功后记录。重复请求返回既有结果，不再次应用效果。

这里枚举的是**程序实际支持的状态更新**，不是凶手动作类别。OperationId 指向固定配置；玩家或模型不能提交新 Transform、代码或任意效果。Open、Unlock 是集合加入；Present 记录向指定人物出示；完成后登记 OperationDone。若调查入口确需先开门或完成其他现场操作，通过 OperationDone 配置，不从名称猜测；不再要求先收集钥匙、试剂或其他刑侦装备。

SetEntityPlace 改变身体实体时，NPC 当前地点也随该唯一位置来源改变；NpcState 不再重复保存 place。实体的绑定节点按其当前地点归属，移动时须同时配置目标场景的合法节点变换／可见性，不能只在文字里说移动。首版 NPC 不自主改日程；未实现的复杂机械动作只展示固定检查结果。

**未来事件的规则：**

1. 开局 occurredEvents 显式给出已发生的真实事件，必须包含所有完全早于开局、且与案件有关的既定历史。时间边界不明确时由作者明确选定，不让程序猜。
2. 开局之后的固定客观事件使用 ScheduledOccurrence，at 必须处于对应 Event.account.when 范围且晚于开局。同一 Event 最多一项计划；不支持本版中凭 AI 临时改凶手或另开事实分支。
3. AdvanceTime 只能前进。按 (at,配置顺序) 逐项触发途中计划，先记录事件发生与世界效果，再按 observations 向明确观察者分发信息；不能把中间所有事件粗略当成“最后才一起得知”。同刻因果顺序必须明确且与 causedBy 一致。
4. 看到事件不等于知道全部真相。PlannedObservation 只发放审核过的信息片段，不自动复制 Event.resultingFacts 给每个人。
5. 没有世界变化需求时 worldEffects 可以为空，事件仅表示剧情事实成为已发生。场景不自动从 process 生成物理动作或血迹。
6. 案后集中复查 Demo 可以将全部案件事件放入初始 occurredEvents、scheduled 为空；这仍保留将来扩展连续事件的结构。

LearnedInformation 是披露包，不是“随便加知识”的公共接口。只有合法检查、报告读取、预设观察或批准的对话可以形成它。Player 的 clues 必须包含完整检查结果；NPC 可以只有片段，confirmedFacts 只增添实际获准信息。

## 12. 人物当前状态、获知与视角修订

```scala
final case class NpcState(
  mood: Text, currentAction: Option[Text],
  perspectiveRevision: PerspectiveRevision,
  perspective: PersonPerspective, memoryRecords: Vector[RecordId]
)
final case class InformationAcquisition(
  id: RecordId, recipient: Observer,
  sources: NonEmptyVector[InformationSource],
  information: LearnedInformation, account: Text
)
final case class PerspectiveChange(
  id: RecordId, person: PersonId, triggerRecords: NonEmptyVector[RecordId],
  beforeRevision: PerspectiveRevision, afterRevision: PerspectiveRevision,
  before: PersonPerspective, after: PersonPerspective, reason: Text
)
final case class NpcMemory(
  id: RecordId, person: PersonId,
  sourceRecords: NonEmptyVector[RecordId], account: Text,
  relatedFacts: Set[FactId], relatedClues: Set[ClueId]
)
final case class NpcExpression(person: PersonId, mood: Text, currentAction: Option[Text])
```

- 初始 NpcState.perspective 等于 Person.initialPerspective，revision=0，memoryRecords 为空；开局不是一条假的玩家请求。本局新增获知以 InformationAcquisition 记录，发生在其所属提交的 storyTime，而主题 when 可以是昨晚。
- InformationAcquisition 是“实际接触到获准信息”的记录，不代表人物接受了结论。私密内情也不因某条线索被出示就自动整包公开。
- 所有改变 perspective 的步骤（新增已知片段、更新猜测、调整策略）均写 PerspectiveChange。before 必须等于提交前当前视角，afterRevision=beforeRevision+1。新增知识只能来自 triggerRecords 中获准信息；改变信念不能凭空添加客观事实。
- before/after 保存主观版本，允许正确、错误、不完整；不要求 after 更接近真相。收到“颜料报告”后可以仍怀疑姐姐，但必须有可解释的心理变化，不强制二值翻转。
- 相同事务可以先记录获知，再记录视角变化，再记录记忆，各记录按 changes 顺序处理；triggerRecords 可引用本提交较早记录，不能引用之后的记录。
- NpcMemory 只记录真实本局交流；来源事实与线索不得超出当时该 NPC 的获知范围。文字记忆不自动成为 Fact。memoryRecords 只保存此人的记忆记录 ID，避免在每个快照复制整份日志。
- NpcExpression 仅更新情绪与语义动作；“擦拭杯子”不会自动删除指纹。真实证据变化必须走固定世界／剧情规则。
- 视角更新由 AI 提议、后端校验引用和获知边界，语义合理性仍需审核；永不写回初始视角或真实历史。

## 13. 实际检查、对话与信息公开

```scala
enum Speaker:
  case Player
  case Npc(person: PersonId)
  case Narrator
final case class InvestigationRecord(
  id: RecordId, point: PointId, playerAction: Text,
  route: Option[RouteId], operation: Option[OperationId],
  result: FindingContent, grantedClues: Set[ClueId]
)
final case class ConversationRecord(
  id: RecordId, speaker: Speaker, listeners: Set[Observer], text: Text,
  presentedClues: Set[ClueId], authoredClaims: Set[ClaimId],
  communicatedInformation: LearnedInformation
)
```

InvestigationRecord 是实际尝试，包括失败／未设定结果；route 和 operation 最多一个有值，成功发现授予线索必须对应合法 route。正文可能描述自由行为，但不能据此执行任意动作。世界状态变化等记录在同提交的相应变化项中；采样／检验过程可记录在 playerAction 和 result，不需要产生背包工具。

ConversationRecord 记录“谁实际说了、谁听见、说了什么”。authoredClaims 只引用此处真实说出的预设说法；临场说谎可以只保存 text，不能为了方便把一句话升格为新 Fact 或任意新 Claim。听到一条预设 Claim，通过 InformationAcquisition.heardClaims 记录；不能自动授予该 Claim 所声称的事实。

communicatedInformation 是本轮**批准披露的范围**，非文本自动事实提取。对每个实际听众分别生成获知记录；某人不在场就不该得到私聊信息。给弟弟看报告与给玩家看报告可以产生不同的线索解读，不能共享一个全局“大家知道了”。

本局公开信息是玩家获知状态的一部分，包括已确认事实、已获得报告／材料、已公开人物资料；日志保留其获取顺序。默认调查方就是当前玩家视角，本版不另建独立“警方全知视角”；以后需要独立警方 NPC 时可用既有 Observer.Npc 表达。

## 14. AI 输入、输出与审计

```scala
final case class CandidateRoute(
  id: RouteId, intent: Text, acceptableApproaches: NonEmptyVector[Text],
  insufficientApproaches: Vector[Text], allowedClues: Set[ClueId]
)
final case class CandidateOperation(id: OperationId, label: Text)
final case class InvestigationAiContext(
  request: RequestId, point: PointId, playerText: Text,
  expectedSequence: Sequence, candidates: Vector[CandidateRoute],
  operations: Vector[CandidateOperation], candidateFacts: Vector[Fact],
  approvedResults: Map[ClueId, FindingContent]
)
enum AiDecision:
  case MatchRoute(route: RouteId)
  case SuggestOperation(operation: OperationId)
  case Insufficient
  case Unspecified
final case class NpcAiContext(
  person: PersonId, profile: PersonProfile, state: NpcState,
  storyTime: CaseTime, calendarOrigin: Option[OffsetDateTime],
  timeOriginLabel: Text, currentPlace: PlaceId,
  permittedFacts: Vector[Fact], perceivedClues: Vector[KnownClue],
  privateContexts: Vector[PrivateContext], memories: Vector[NpcMemory],
  permittedClaims: Vector[Claim], recentConversation: Vector[ConversationRecord],
  disclosureRoutes: Vector[CandidateRoute]
)
final case class NpcReplyDraft(
  person: PersonId, text: Text, usedFacts: Set[FactId], usedClaims: Set[ClaimId],
  matchedRoutes: Set[RouteId], expression: NpcExpression,
  proposedPerspective: Option[PersonPerspective]
)
enum AiCallStatus:
  case Completed, TimedOut, InvalidOutput, ProviderFailed, Rejected
final case class AiCallRecord(
  id: AiCallId, request: RequestId, session: SessionId,
  basedOnSequence: Sequence, purpose: Text, model: Text, promptVersion: Text,
  startedAt: Instant, finishedAt: Instant, status: AiCallStatus,
  inputDigest: Text, output: Option[Text], rejectionReason: Option[Text]
)
```

调查判断模型只获得已通过入口、硬条件、已发生事件检查的候选；其决定必须匹配当前请求集合。SuggestOperation 不执行动作，只提示玩家选择已登记操作。Insufficient／Unspecified 不授予线索。每个明确 Fixed 请求可以直接执行，不需要 AI。

NpcAiContext.storyTime/currentPlace 由本局状态投影，不新增地点真值；calendarOrigin+timeOriginLabel 用于解释“昨晚”等相对时间，未提供公历原点时按剧情日／相对时段表达，模型不能擅自假定日期。NpcAiContext 使用本人视角和允许的历史交流，不自动加载真实事件版本。profile 是该 NPC 自己知道的人设，不能包含作者额外标注但角色本不知的真相；permittedFacts 按他的初始／本局真实知情范围筛选。privateContexts 仅展开其当前视角已知的内情，memories 仅解析本人 memoryRecords；recentConversation 只包含其实际在场、听到或参与的内容。知情也不等于愿意披露，玩家可得关键结果仍通过 disclosureRoutes 判定。

NpcReplyDraft 是建议，不能直接修改 GameState。usedFacts／usedClaims 必须在允许输入中；matchedRoutes 必须符合当前条件。proposedPerspective 可调整主观事件与策略，但新增 knownFacts／线索／内情必须有本次实际获知记录，不接受模型自造实体或事实。NPC 内心改变不自动向玩家公布。

**自由文本的语义无法仅靠 ID 校验保证。** 模型即使填了合法 usedFacts，也可能在 text 中泄露秘密或说出新事实。首版关键检查结果使用固定 FindingContent；自由对白须经过语义审核，不通过时回退到获准内容的固定答复。不能宣称使用强类型就完全防止幻觉。玩家输入、记忆及案件文字作为数据处理，不作为执行指令。

AiCallRecord 独立于成功提交日志保存失败调用，不占据游戏提交序号；inputDigest 是提示输入的摘要，不存密钥。output、拒绝原因和提示内容可能包含秘密，只能供受限调试使用，不作为玩家 API 返回。模型超时和失败不产生部分状态变更。

## 15. 推理、指控与结果

```scala
enum Premise:
  case ClueFound(clue: ClueId)
  case FactEstablished(fact: FactId)
final case class DeductionRule(
  id: RuleId, premises: NonEmptyVector[Premise], conclusion: FactId
)
final case class AccusationOption(
  id: OptionId, question: QuestionId, label: Text,
  hypothesis: Text, visibleWhen: Condition
)
final case class AccusationQuestion(
  id: QuestionId, incident: IncidentId, label: Text,
  minimumSelections: Int, maximumSelections: Int,
  options: NonEmptyVector[OptionId]
)
final case class SolutionRequirement(
  question: QuestionId, incident: IncidentId, conclusion: FactId, label: Text,
  correctOptions: NonEmptyVector[OptionId],
  acceptedClueSets: NonEmptyVector[NonEmptyVector[ClueId]], weight: Int
)
final case class Accusation(
  incident: IncidentId, selections: Map[QuestionId, Set[OptionId]],
  supportingClues: Set[ClueId], explanation: Option[Text]
)
final case class RequirementResult(
  question: QuestionId, incident: IncidentId, conclusion: FactId, label: Text,
  passed: Boolean, awardedPoints: Int
)
final case class Verdict(
  requirements: Vector[RequirementResult], score: Int,
  maximumScore: Int, passed: Boolean
)
final case class AccusationRecord(
  id: RecordId, answer: Accusation, verdict: Verdict
)
```

线索直接确认的事实先加入玩家知识；规则为 AND 前提，多条规则得出同一结论为 OR，固定点迭代至集合稳定。禁止从整案 Fact 表直接生成全知状态。

acceptedClueSets 外层 OR、内层 AND，weight > 0。判定要求问题属于相应 Incident，选项属于对应问题且当前可见、满足选择数量限制，所选集合与服务端 correctOptions 一致，证据均已发现、至少覆盖一组获准组合，且从开局公开事实和提交证据能经规则推出目标 conclusion。每个问题 minimumSelections ≥ 1、maximumSelections ≥ minimumSelections，且不超过选项数；correctOptions 不重复、均属于该问题并符合选择数量，问题 ID 在包内唯一。每条通过得 weight 分，score 为求和，maximumScore 为本次所评 Incident 的全部要求权重之和；passed 表示该 Incident 所有要求通过。Scored 配置下整案完成需要 assessment.requiredIncidents 中所有 Incident 分别通过，不以一次局部通过自动结案。

错误假设不能伪装成 Fact：选项使用独立 OptionId 和 hypothesis，正确答案才由服务端 SolutionRequirement 关联 FactId。候选显示由作者显式配置，不能从 Incident.affectedPeople 自动生成——受影响者可能只有死者，凶手并不必然在此集合。可以展示“A 实施了谋杀”作为一个与其他人物对称的待选假设，但不能标注它正确或只依据正确答案生成候选。

自由文字解释由 AI 辅助映射到固定 OptionId 并经玩家确认，评分仍依据服务端固定事实和答案。未结案时不能返回全部正确结论或缺失证据；参考 Verdict 是后端保存形态，对外须投影。结案与复盘读取绑定案件修订，不让模型现场改凶手。

## 16. 会话当前状态、统一变化日志与快照

### 16.1 身份、进度和当前投影

```scala
final case class UserProfile(id: UserId, nickname: Text, createdAt: Instant)
enum GameMode:
  case Detective, SuspectChallenge
final case class PlayerRole(mode: GameMode, character: Option[PersonId])
enum SessionStatus:
  case Active, Completed, Abandoned
final case class GameSession(
  id: SessionId, owner: UserId, caseId: CaseId, caseRevision: Revision,
  role: PlayerRole, status: SessionStatus, createdAt: Instant, updatedAt: Instant
)
final case class WorldState(
  entityPlaces: Map[EntityId, PlaceId], opened: Set[EntityId],
  unlocked: Set[EntityId], nodeOverrides: Map[SceneNodeId, Transform],
  visibleNodes: Set[SceneNodeId]
)
final case class PlayerKnowledge(
  facts: Set[FactId], clues: Set[ClueId], heardClaims: Set[ClaimId],
  availableReports: Set[ReportId], materials: Set[AssetId]
)
final case class GameState(
  sequence: Sequence, storyTime: CaseTime,
  playerPlace: PlaceId, playerPosition: Vec3,
  knowledge: PlayerKnowledge, world: WorldState,
  occurredEvents: Map[EventId, CaseTime], completedOperations: Set[OperationId],
  presented: Map[PersonId, Set[ClueId]], npcs: Map[PersonId, NpcState],
  passedIncidents: Set[IncidentId]
)
```

GameSession 绑定用户和案件修订；GameState 只保存当前状态。SessionStatus 是生命周期，不能当作故事阶段。GameMode 是两个实际入口协议，不是人物职业或故事身份。Detective 时 character 可空；SuspectChallenge 时必须指定可扮演人物。挑战的专属胜负规则可另加规则模块，当前证据链评分首先用于侦探模式，不假装已定义所有嫌疑人挑战规则。

身份服务负责验证令牌。业务消息的 owner／userId 必须来自路由边界校验后的可信身份，不接受客户端自报用户身份。账户认证凭据和第三方令牌不混入案件聚合，本版不设计密码系统。

entityPlaces 是本局唯一的实体地点真值；人物地点通过 Person.body 查它，避免 NpcState.place 与实体位置分叉。人物情绪／当前行动／主观故事在 NpcState 内，客观历史不受它们改变。界面可读取当前投影，不能从 NPC 文本行动猜测身体实际移动。

### 16.2 可重放的变化记录

```scala
enum SessionChange:
  case WorldChanged(effects: NonEmptyVector[WorldEffect])
  case PlayerMoved(place: PlaceId, position: Vec3)
  case OperationCompleted(operation: OperationId)
  case CluePresented(person: PersonId, clue: ClueId)
  case StoryTimeAdvanced(to: CaseTime)
  case CaseEventOccurred(event: EventId, at: CaseTime)
  case Investigated(record: InvestigationRecord)
  case Spoke(record: ConversationRecord)
  case Acquired(record: InformationAcquisition)
  case FactsEstablished(facts: Set[FactId])
  case PerspectiveRevised(record: PerspectiveChange)
  case MemoryAdded(record: NpcMemory)
  case ExpressionChanged(expression: NpcExpression)
  case Accused(record: AccusationRecord)
  case StatusChanged(status: SessionStatus)
final case class SessionCommit(
  meta: CommitMeta, reducerVersion: ReducerVersion,
  changes: NonEmptyVector[SessionChange]
)
final case class Snapshot(
  session: SessionId, caseId: CaseId, caseRevision: Revision,
  atSequence: Sequence, reducerVersion: ReducerVersion,
  state: GameState, status: SessionStatus,
  createdAt: Instant
)
```

SessionChange 枚举的是后端支持的变更协议，不是案件历史动作。新增一种犯罪方式不要求新增变化分支；只在确实新增运行能力时扩展。玩家检查、对话、获知、误解修订可在同一个 SessionCommit 中按顺序发生。

**统一规则：**

1. 普通成功请求形成一个 SessionCommit，序号=当前序号+1；时间推进可形成按时刻排序的多条提交。日志、当前投影、会话状态与请求回执在同一数据库事务提交。失败不留下部分更新，单纯有效但无发现的检查也可留一个 Investigated 记录。
2. Commit 中 changes 从前到后应用；同提交引用仅允许指向更早的变化记录或既有记录。禁止引用未来获知；重放不调用 AI，不重新解释 process，也不重新随机生成对白。
3. Acquired 是获知依据：接收者为 Player 时更新 PlayerKnowledge；为 NPC 时只建立获知记录，其知识变化统一由后续显式 PerspectiveRevised 写入，避免重复更新同一视角。报告／材料访问权可由获知包增加；合法线索先加入直接确认信息；玩家的进一步推导由 cases 模块的公开内部消息预先求值，并作为 FactsEstablished 保存，只更新 PlayerKnowledge。NPC 的知情与信念变化仍由 PerspectiveRevised 保存。该变化只能写入经批准、已发生的结论；重放只按集合并入，不跨服务调用规则或模型。
4. 同一提交中，NPC 获知与视角修订必须配套；after 只能使用此前获知和原有视角，已确认客观知识不因新猜测被删除。AI 只建议新的信念／解释，后端核对可用事实与来源。
5. StoryTimeAdvanced 和 CaseEventOccurred 由第 11 节的固定计划处理。较大时间跳跃可以在一个事务中按经过的剧情时刻生成多组变化；获知时间需使用单独提交或记录内明确时间，不能全挂在最终时刻。本版默认拆成按剧情时刻的系统提交，外部请求通过回执关联整批提交。
6. User 操作和系统推进需要同一会话顺序协调；模型调用前后比较 sequence，过期结果重新筛选候选或返回重试，不能覆盖新状态。
7. Snapshot.state.sequence=atSequence。恢复时加载最近快照，再按序应用其后日志；状态查询“某个序号时”同理。重放函数必须使用绑定案件修订和同一 reducer 版本，见第 18 节。
8. 检查／对话／获知等独立记录表可以是日志的索引投影，不应再有一份可独立改写、与日志矛盾的真值。日志和主观 before/after 可保密，仅向获准对象生成复盘投影。

不要求每秒保存快照，也不要求每个类有自己的时间系统。初始序号0快照、重要剧情节点及必要的定期快照足以支持 Demo；日志保存其间变化。

### 16.3 请求去重

```scala
enum RequestStatus:
  case Pending, Completed, Failed
final case class RequestReceipt(
  request: RequestId, session: SessionId, owner: UserId,
  payloadDigest: Text, expectedSequence: Sequence, status: RequestStatus,
  committedSequences: Vector[Sequence], responseRecord: Option[RecordId],
  createdAt: Instant, finishedAt: Option[Instant]
)
```

同一会话 RequestId 必须唯一，相同 ID 的载荷摘要不同直接拒绝。Completed 返回已记录结果；Pending 不重复发起模型或世界变化。一次时间推进可关联多条系统提交；如要求整段原子性，可在同一事务写整批提交，但仍保持各自序号和剧情时刻。暂停／崩溃后按已提交部分恢复，不能将已发生事件再执行一遍。

RequestStatus 是请求执行状态，与 SessionStatus 不同。回执没有永久存明文令牌；responseRecord 可关联检查／对话／指控结果，只有世界变化的请求可以为空并从提交序号恢复成功响应。

## 17. 完整案件包与初始化

v1.4 增加 AssessmentSpec：仅调查的示例使用 InvestigationOnly；带结案评分的案件使用 Scored，必须明确必答事件及非空答案。它是引擎支持的两种评分协议，不是案件内容分类。仅调查不允许提交指控，也不因“零个必答事件均通过”自动结案；它保持 Active，直到显式结束／放弃。正式首版案件仍须配置 Scored。

InvestigationOnly 的 questions、options 必须为空；Scored 的每个 requiredIncident 必须存在且有答案覆盖，每条答案的问题、事件、选项和结论必须有效。不必答事件可以保留内容，整案通过仅检查 requiredIncidents。此结构调整需新 schemaVersion；已有存档不得静默按新形状解析。


```scala
final case class SchemaVersion(value: Int)
final case class ReducerVersion(value: String)
enum SourceKind:
  case Original, Adapted, GeneratedAndReviewed
final case class Source(id: SourceId, kind: SourceKind, reference: Text)
final case class CaseTruth(
  background: Text, persons: Vector[Person], privateContexts: Vector[PrivateContext],
  motives: Vector[Motive], events: Vector[Event], incidents: Vector[Incident],
  topics: Vector[Topic], facts: Vector[Fact], claims: Vector[Claim],
  sources: Vector[Source]
)
final case class InitialNpcSetup(person: PersonId, mood: Text, currentAction: Option[Text])
final case class InitialGameSetup(
  spawnPlace: PlaceId, spawnPosition: Vec3,
  opened: Set[EntityId], unlocked: Set[EntityId],
  occurredEvents: Map[EventId, CaseTime], npcs: Vector[InitialNpcSetup]
)
enum AssessmentSpec:
  case InvestigationOnly
  case Scored(
    requiredIncidents: NonEmptyVector[IncidentId],
    solution: NonEmptyVector[SolutionRequirement]
  )
final case class InvestigationSpec(
  clues: Vector[Clue], routes: Vector[DiscoveryRoute],
  operations: Vector[Operation], rules: Vector[DeductionRule],
  questions: Vector[AccusationQuestion], options: Vector[AccusationOption],
  initial: InitialGameSetup, scheduled: Vector[ScheduledOccurrence],
  assessment: AssessmentSpec
)
final case class CasePackage(
  id: CaseId, revision: Revision, schemaVersion: SchemaVersion,
  reducerVersion: ReducerVersion, title: Text,
  initialStoryTime: CaseTime, timeOriginLabel: Text,
  calendarOrigin: Option[OffsetDateTime], assets: Vector[Asset],
  world: WorldSpec, truth: CaseTruth, publicInfo: PublicSpec,
  scene: SceneSpec, investigation: InvestigationSpec
)
```

初始化必须是确定性步骤：

1. 验证案件为已发布、绑定具体修订和 reducerVersion。建立 GameSession，核验 UserId；按所选模式校验角色，不给其他人读取嫌疑人秘密。
2. GameState.sequence=0，storyTime=initialStoryTime；玩家位置、开合和解锁状态读 initial；entityPlaces 由 Entity.initialPlace 建立；nodeOverrides 为空，visibleNodes 来自 initiallyVisible 的节点。
3. occurredEvents 读 initial；每个实际时刻须处于相应 Event.account.when 内，且不晚于开局；未来 ScheduledOccurrence 不写入该集合。
4. PlayerKnowledge.facts 从 commonKnowledge 起步，clues／heardClaims 为空；availableReports、materials 来自公开背景。可打开报告不等于已经读过；取得报告内容须走合法读取路线，随后按已批准披露包加入 verifiedFacts／claims。不要开局遍历全部报告事实。
5. NpcState 从人物 initialPerspective 初始化，perspectiveRevision=0，mood/currentAction 读 InitialNpcSetup，memoryRecords 为空；initialAwareness 是历史来源，不伪造本局日志。每个可访问 NPC 必须恰好有一份配置，引用已发生信息。
6. completedOperations、presented、passedIncidents 为空；按初始知识运行纯推导。建立序号0 Snapshot，不在此时调用 AI 再造人物或真相。

Published 案件的编辑发布状态可由案件服务的元数据表维护，不在领域真相中混入可变 isPublished。schemaVersion 用于解析结构，reducerVersion 必须同时匹配 CasePackage、SessionCommit 与 Snapshot，用于确定日志解释规则；改变执行含义不能偷偷重放旧日志，须保留旧 reducer 或做经过验证的迁移。

## 18. 领域错误与纯函数边界

```scala
enum ErrorCode:
  case MissingReference, InvalidValue, InvalidTime, ConflictingContent
  case AccessDenied, ConditionFailed, FutureInformation, UnsupportedOperation
  case StaleState, RequestConflict, InvalidAiOutput, AiUnavailable
  case UnreachableRequiredClue, ReplayVersionMismatch, ValidationIncomplete
final case class ValidationIssue(code: ErrorCode, path: Text, detail: Text)
final case class GameError(code: ErrorCode, publicMessage: Text)
```

请求与消息草案已移至 [api_design_draft.md](api_design_draft.md)。移动仍遵循第 22 节的空间约束；若移动消耗剧情时间，中途预设事件须按序处理，不从未模拟的插值位置产生额外目击事实。

| 纯函数 | 输入 | 输出与职责 |
|---|---|---|
| validateCase | CasePackage | Vector[ValidationIssue]；结构与可计算一致性 |
| buildSceneAuthoringInput | 案件修订、目标 SemanticScene、相关要求及获准事实 | 场景制作输入；明确事实、要求与美术自由度，不在运行时调用 |
| validateSceneReview | 案件修订、实际场景清单摘要、SceneReview | 检查绑定覆盖、结论完整与版本匹配；不把结构校验当成视觉验收 |
| buildInitialState | 案件包、已验证角色 | GameState；不随机生成案件 |
| availableRoutes | 案件包、当前状态、入口 | 获准候选 RouteId；过滤未来与硬条件 |
| buildInvestigationContext | 已验证请求、当前候选 | InvestigationAiContext；最小化秘密范围 |
| validateAiDecision | 请求候选、当前状态、AiDecision | 批准路线／GameError；不评价全部现实语义 |
| planOperation | 案件包、状态、操作 ID | 已批准的有序变化项／GameError |
| planTimeAdvance | 案件包、状态、目标剧情时间 | 按剧情时刻分组的提交计划 |
| applyChange | 已批准变化、当前状态 | 新 GameState 与必要的会话状态更新；重放不调用模型或重新推导 |
| deriveFacts（cases 内部） | 当前可用事实／证据、规则、已发生事件 | 固定点知识集合；获准结论记录为 FactsEstablished，具体跨模块调用待实现设计 |
| evaluateAccusation | 参考答案、当前知识、指控 | Verdict；固定 ID 与证据组合判定 |
| buildPlayerView | 案件包、当前状态、已验证角色 | 安全 DTO；不返回完整聚合 |

上表描述领域纯函数的职责，不是已生成的 Scala 方法。消息、服务归属和传输实现另见 API 草案。

领域纯函数不读取数据库、真实时间或网络，不生成随机 UUID，也不调用 AI；这些输入与已批准结果由副作用边界提供。

## 19. 时间交织的完整例子

以下为自拟例，与《月光》原作无关。剧情原点可换算为公历时间，也可只展示“昨晚／今天”。

| 阶段 | 客观历史 | 弟弟的认知 | 本局日志 |
|---|---|---|---|
| 昨晚20:40 | Event murder：另一人实施杀害 | 弟弟不知道死亡发生时间 | 历史 Event，不是今天的玩家操作 |
| 昨晚21:10 | Event retrieve_letter：姐姐取信后离开；袖口是颜料 | 看到红色污渍，以为是血 | InitialAwareness 记录接触线索的时间和来源 |
| 开局今天09:00 | 历史均已发生，初始 occurredEvents 包含两件事 | BelievedEvent sister_attack：以为姐姐21:00左右失手杀人，想保护她 | 序号0快照；初始视角与公开背景分离 |
| 今天09:05 | 客观过去不变 | 弟弟对玩家声称姐姐整晚与他在一起 | Spoke 记录谎言；玩家听到 Claim，但不把它当 Fact |
| 今天09:15 | 客观过去不变 | 尚未收到检验信息 | 玩家 Investigated＋Acquired，获得“污渍是颜料”线索；弟弟不自动全知 |
| 今天09:20 | 客观过去不变 | 看到玩家出示报告，开始动摇但仍隐瞒取信秘密 | CluePresented、Spoke、NPC Acquired、PerspectiveRevised(0→1)、MemoryAdded |
| 今天09:30 | 客观过去不变 | 取得真实死亡时间后，认为姐姐可能不是凶手，准备承认部分谎言 | 新获知＋视角修订(1→2)，保存 before/after 和触发依据 |

关键链路：真相 Event 与主观 BelievedEvent 共用 EventAccount，但拥有不同 ID 和因果图。未来发现不会改写昨晚 Event；它改变的是今天的 PersonPerspective、对外说法和玩家知识。

“缺失线索”也遵循同一机制：地板模型没画血迹没有意义；Topic 的 Fact=Absent、规定范围内完成检查并记录获知后，才形成可用线索。若 Fact=Unspecified，记录一次无确定结果的尝试，不建立排除事实。

若扩展为连环事件实时发生：今天10:00的后续事件预先存在于 CaseTruth，但开局不在 occurredEvents。推进到10:00时写 CaseEventOccurred、预设世界变化与明确目击者的获知；其他 NPC 和玩家仍须实际调查，不能因为世界发生就自动得知。

## 20. 存储归属、查询与验证

### 20.1 建议存储集合（不等同于一类型一表）

| 归属 | 建议集合 | 约束 |
|---|---|---|
| accounts | 用户资料、认证会话 | 令牌验证在路由边界；不与案件真相混存 |
| cases | 案件修订元数据、定稿聚合／按域拆表、资产目录 | CaseId+Revision 唯一、发布后不可改 |
| cases | SceneReview 制作验收记录 | 按案件修订和 sceneFingerprint 关联；历史验收可保留，只有当前场景摘要的通过结果有效 |
| games | GameSession、当前 GameState、序号0及后续 Snapshot | SessionId 归属可信用户，修订与 reducer 版本固定 |
| games | SessionCommit、按 RecordId 建立的记录索引 | SessionId+Sequence 唯一，记录可定位到提交及序号内位置 |
| games | RequestReceipt | SessionId+RequestId 唯一，载荷摘要冲突拒绝 |
| system／games | AiCallRecord | 独立记录失败与被拒绝调用；不返回秘密给玩家 |

查询“昨晚发生了什么”读取固定 Event 与该会话已发生范围；查询“弟弟09:20之前相信什么”按提交序号读取最近快照并重放到目标点；查询“玩家什么时候知道血迹是颜料”查 InformationAcquisition 的接收者及所属 CommitMeta，不看 Fact 的主题时间。

剧情时间相同可能有多次获知与修订，所以审计接口优先接受 Sequence，或明确“该剧情时刻最后一次提交之前／之后”；不能仅用 storyTime 当唯一键。快照、记录及 AI 上下文中的秘密须按角色和结案权限投影。

### 20.2 发布与执行检查清单

- 全部 ID 定义存在；引用在绑定修订内；来源、报告、节点、线索、主观事件和记录可解析。主体 body 指向实际实体，不同人物不共用身体。
- 地点包含无环；坐标、资源类型、时间区间合法；真实因果和本人主观因果分别无环，不把 EventId 当 BelievedEventId。主观错误本身不构成校验失败。
- Topic 的时间与范围清楚，重复主题不冲突；Unspecified 不进入公开知识、线索、推理结论或参考答案；关键缺失有明确审核依据。
- 初始知情范围有 InitialAwareness；PrivateContext 的 initiallyKnownBy 与初始视角相符；只见过线索片段不能获得全检验结果；初始来源不能引用本局记录。
- 所有开局已知事实依赖事件均已发生；后续 Fact、报告 verifiedFacts、推导结论与披露结果检查 requiresOccurred；预设观察者不能接收尚未发生的信息。
- 初始世界、人物位置、节点和已开门等一致；脚本世界效果已实现，移动实体的节点绑定同步；不得从语义动作文本直接更新物理状态。
- 每个 SceneRequirement 的实体、事实、入口引用有效，绑定完整且属于约定时段的场景；影响推理的布置不能仅藏在 decorativeNotes。要求与固定事实矛盾时阻止发布，不由制作 AI 修改真相。
- 所有关键细节有对应 sceneFingerprint 的完整通过验收；旧摘要、缺项和失败不视为通过。坐标结构检查不能替代视线、显示内容与实际交互的预览验收。
- Route 硬条件可计算，关键线索有 Fixed 可达路线，前置不能循环锁死。连续几何可达需人工／浏览器验收，不宣称仅靠依赖图验证全部 3D 路线。
- 报告的陈述与 Fact 分开，公开资料不含秘密；后续披露均有获知记录。对话听众、NPC 内心和玩家知识不混用。
- PerspectiveChange 的版本和 before 正确，新增确认知识有来源；允许不接受反证，但不能凭心理修订创建客观事实或改历史。
- 事务中先获知后修订／记忆，引用不能指向后续记录；请求去重、并发序号和日志投影一致。重放只用批准结果，不再次调用 AI。
- 每组参考证据组合确实可以推出对应结论，且所依赖事件可在规定流程中发生；结案检查按具体 Incident，不把局部通过当整案完成。
- AI 结构正确不等于叙事正确；语义一致性、图像是否符合提示、自由对白泄密与现实因果另行审核，失败有固定检查／固定答复回退。

### 20.3 每个枚举保留的理由

| 枚举 | 有限边界 |
|---|---|
| Actor、Observer、Speaker | 提交人、获知人、说话者的数据通道；人物仍用 PersonId |
| Subject、InformationSource | 当前系统支持的引用目标；不是世界事物或信息语义分类 |
| Existence、FactValue | 存在性状态与数据编码形态，开放细节由 AuthoredDetail 表达 |
| AssetKind、Entry | 已实现的资源加载器和访问入口 |
| Condition、Premise | 确定性求值器支持的前提协议 |
| DiscoveryMethod | 固定检查与语义匹配两条执行路径 |
| WorldEffect、OperationEffect、SessionChange | 程序确实实现的状态变更及日志重放协议 |
| AiDecision、AiCallStatus、RequestStatus | 模型响应与调用／请求执行状态 |
| GameMode、SessionStatus | 当前产品入口与会话生命周期 |
| SourceKind | 本项目记录原作、改编和生成审核来源的方式 |
| ReviewOutcome | 制作验收的待检查／通过／失败三态 |
| AssessmentSpec | 仅调查／带参考答案评分两种有限执行协议 |
| ErrorCode | 当前校验器与运行规则能够报告的有限错误类别；请求协议另见 API 草案 |

不恢复 EventKind、MotiveKind、ClueKind 等开放领域枚举。新增现实动作、关系或线索不应迫使修改这些程序协议；确实新增执行能力时才扩展对应协议和处理器。

## 21. 本版边界与参考

这是一版覆盖案件、调查、人物视角及运行记录的完整**领域初稿**，不等于所有功能已实现。当前优先完成侦探模式与案后集中复查；实时后续事件可先只保留类型，嫌疑人挑战专属计分、复杂自主 NPC 日程、多人联机和账户供应商细节另行设计，不为它们预造大框架。

[theNPC](https://github.com/Rainytroy/theNPC)用于参考语义世界、人物档案与动态状态分离；[CyberQuest](https://github.com/ReinVelt/CyberQuest)用于参考画面／热点／条件／结果分离。此文类型为本项目设计，不宣称两个仓库提供现成的强类型 3D 世界模型，也不复制其任意字典参数或场景回调直接改状态的方式。

**仍不做完整物理模拟：** 不计算每滴血，不穷举所有线索及发现动作，不把场景缺少表现当成不存在。历史是固定依据，AI 解释语义，后端控制获知与变化，场景提供可调查的近似表现。

## 22. 空间与制作交接补充（v1.3）

### 22.1 坐标、父链和移动校验

每个 Place 有独立局部原点，当前房间的渲染、playerPosition、Entry.World.center、连接入口及 walkable 都使用该坐标系。不假定不同房间的坐标可以直接比较。

SceneNode.transform 是相对 parent 的局部 TRS，无 parent 时相对 Place。parent 必须存在、无环，父子属于同一 Place，且当前可见子节点不能绕过隐藏父级泄露；节点覆盖也使用局部变换。`placeMatrixOf(node)` 按父链求矩阵，`placeMatrixOf(parent) × localMatrix(node)`；OnNode 热点使用该矩阵乘 localPoint。单位和旋转约定沿用第 2 节。

Matrix4.values 固定16个有限数值、列主序，初始由 Transform 转换。父级非均匀缩放加旋转可能产生剪切，不能保证合成矩阵仍能无损拆回 Transform；因此合成后的节点变换使用 Matrix4，而不是把合成结果强塞回欧拉角。世界效果修改父级时几何子级自动跟随，语义实体位置仍由实体状态更新，不能把几何父子关系当作“物品一定属于某人”的推理依据。

SceneBounds 是轴对齐盒，各轴 min < max。可探索 Place.walkable 必须至少一个区域，非探索地点可为空。当前移动契约采用“客户端基础碰撞＋后端检查房间开放与可行走区域”；跨房间只能沿开放 Connection，由对应 fromEntry／toEntry 落点进入，不能由客户端选择任意新房间坐标。AABB 是近似白名单，不宣称它等同完整导航网格或严密防穿墙；服务端还校验有限数值、入口距离和同房间每次移动距离不超过 SceneSpec.movement.maximumStepMeters（必须 > 0）。这只是单步距离限制，不声称能阻止高频请求或所有路径穿墙。若以后简化为只信任客户端位置，必须明确降低保证，不能沿用“权威遮挡已验证”的表述。

### 22.2 制作输入

```scala
final case class AssetLibrary(available: Vector[Asset])
final case class SceneAuthoringInput(
  caseId: CaseId, caseRevision: Revision, scene: SemanticScene,
  entities: Vector[Entity], relevantFacts: Vector[Fact],
  cues: Vector[SceneCue], supportedOperations: Vector[Operation],
  allowedAssets: AssetLibrary, bounds: SceneBounds, freeRein: Vector[Text]
)
```

requirements 已在 scene 中，不复制第二份。relevantFacts 要展开获准事实内容，不只提供模型无法理解的 FactId。allowedAssets、cues、supportedOperations 允许为空；空场景或没有音频的场景不应为了满足非空类型而造素材。freeRein 从 decorativeNotes 得出，不能与关键要求相冲突。人工与 AI 均可按此交接物制作；不撤销用户已确定的“AI 在制作阶段写场景代码／资源”的方向。

## 23. 信息披露与调查反馈边界

具体玩家响应类型已移至 [API、消息与传输类型草案](api_design_draft.md)。领域层仍保留以下约束：

InquiryPoint.fallbackFinding 是预设的未确认反馈：只有无候选、无对应 Fixed 操作、匹配失败或模型失败时使用；存在合法候选不能一律跳过匹配直接回退。缺省全局话术为“本次检查尚不能确认更多信息”，不能用“没有痕迹”表达事实缺失。fallback 不授予线索，不能泄露未发现内容。

Clue.suggests 只是作者配置的质询建议，不是只能向这些人出示的权限白名单。ClueView 只在发现后展示，并过滤尚未公开的人物身份；也可以隐藏全部建议，让玩家自行判断，不能以推荐名单提前泄露秘密关系。

候选假设不等于已确认事实。参考答案、正确选项、充分证据组合和人物秘密不能随候选列表披露；完整真相复盘必须检查会话权限及结束状态。玩家当前状态是内部状态的获准投影，不能直接公开完整 CasePackage、GameState 或人物私密视角。

## 24. 文档分工

API 消息清单、请求包装、玩家响应类型，以及服务归属／路由／编解码／事务实现备忘已移至 [api_design_draft.md](api_design_draft.md)，均待后续讨论。本篇继续讨论领域类型及其不变量，不以接口建议反向锁定类型设计。

日志自足、版本明确与重放确定性仍是领域约束：SessionCommit 保存已批准的记录与结论；不能依赖可独立修改的外部记录或恢复时再次调用 AI／推导服务来重建历史。新增可选字段或枚举成员不自动保证旧日志兼容。

## 25. 当前类型范围与复杂手法表达（v1.3）

已确认：首版换成现代原创案件，借鉴关系与误导结构，案情随后设计；侦探默认常用现代刑侦能力，无装备收集门槛。尚未确认：取消嫌疑人模式／生成页面、限定单房间、固定15页如何替换、取消 AI 制作场景等，不因另一 agent 的建议而自动执行。

静态单案是可选实现配置：scheduled 为空、所有既往事件已发生、只评一个 Incident。它不意味着人物认知、对话和已获信息不再变化，也不意味着所有 storyTime 必须永久冻结。多 Incident 可以按现有接口逐个提交，并不必然需要改成 Vector[Accusation]。

特殊手法可分三个层次处理：

1. **固定事实可表达：** 时钟被改、录音延时、凶器被替换等用 Event／Fact／Claim 表达，证据通过检查报告取得，不需要完整物理仿真。
2. **预制表现可展示：** 关键机关用局部模型、特写、预制动画或已登记 Operation 辅助玩家理解，仍由固定事实判定。
3. **自由操作才需计算：** 若玩家可以任意改装机关并验证不同后果，需要该机关专用可计算模型与验收；这是新增能力，不能让 AI 口头判断代替仿真。

关系复杂的案件能更直接利用人物视角和审讯，是首版成本选择；不是本架构无法表达其他案件。现代原创案还应检查现代检验能否合理挑战伪证，不以“剧情需要”无依据拒绝已有技术，也不把检测结果未设定当成没有结果的事实。

## 26. v1.4 最小示例与验证边界

最小示例见 [minimal_investigation.json](../fixtures/minimal_investigation.json)，说明及命令见 [minimal_fixture.md](minimal_fixture.md)。采用完整 CasePackage 形状和 InvestigationOnly 配置，不伪造非空答案；保留一条真实事实及其主题供线索引用。尚无人物／犯罪历史不代表真实案件类型被删除。

此阶段只验证契约结构、示例涉及的引用与静态访问链，以及声明的有序日志能否恢复相同知识。文档校验工具不是生产发布校验器，不证明几何可达、资产真实存在、语义正确、数据库事务或服务端并发已实现。三维资产使用待制作占位 URI；发布场景仍须真实资源与对应版本验收，不能把夹具结构通过当成发布批准。

进入阶段基线：优先完成这条调查路径；后续能力在使用前局部完善。安全越界、历史一致性或持久化问题允许随时修复，不以冻结为由保留已知错误。API 仍是独立草案。


> 2026-10-27 实施同步：最小 Scala 切片已按四张表重构，具体实现及验收命令以 [minimal_backend.md](minimal_backend.md) 为准。当前状态直接存储；旧提交日志重放仅用于离线迁移。导入入口为 ImportCaseMessage，操作和调查直接返回 CommandResult，不再增加 result 包装。本文件中尚未实施的完整领域或接口仍是设计草案。
