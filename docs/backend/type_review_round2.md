# 领域类型第二轮评审与修改意见（针对 `case_truth_types.md` v1.3）

> **本文性质**：评审意见，不是定稿，不修改类型本身。作者确认后按第 5 节的清单出 v1.4。
>
> **评审对象**：`case_truth_types.md` v1.3（1009 行、153 个类型定义）。
>
> **评审方式**：一名独立评审 agent 全文通读并独立出提案 → 本机复核者逐条反驳裁决。本文只记录**裁决结果**，两边原始提案差异保留在第 3、4 节。
>
> **范围**：仅领域类型及其不变量。API／消息／传输类型见 `api_design_draft.md`，本轮不评，也不据其反向约束领域类型。
>
> **参考**：`../../plan.md`、`../../AGENTS.md`、`../../../samples/samples/rules.txt`、`../cases/`。
>
> **前序文件**：`case_truth_types_changes.md` 是第一轮意见，其正文保留用于追溯；本文第 1 节更正其中若干判断，第 5 节的清单取代其 P0 清单。

---

## 0. 结论摘要

v1.3 在定稿内容、人物视角、会话日志、验收门槛、特殊手法分层（§25）这几个方向上已经稳定，本轮不再动。

评审发现 **5 项 P0 与 9 项 P1**。其中：

- **2 项**（空间通行语义、入口可用性）是 v1.3 自身的漏洞，两份提案都没提；
- **3 项**（`Condition` 缺"被问到某主题"分支、`Unspecified` 与结论引用脱节、`FactsEstablished` 缺来源链）由独立评审提出，本机复核后**全部采纳**；
- **7 项**独立评审提出的改动被**驳回**，理由见第 3 节——主要是把"程序不需要解释的语义"误当成了可校验性缺口，以及把"非运行时类型"等同于"不属于领域文档"。

关于 `Connection`（P0-1）：**它现在并没有枚举连接方式，这是对的，必须守住**；但它缺 `ConnectionId`、缺"已发现"状态、缺通用准入条件、缺入口半径，这四个是真实的表达缺口，且都能用既有 `Condition` 机制补齐，不需要引入任何 mode 枚举。

> **本轮之后进入开工阶段。** 只做第 5 节的 5 项 P0，P1/P2 一律冻结，界线与解锁条件见第 8 节。

另有一项方法论结论值得记住：**两份提案都曾试图拆掉 `Topic:Fact` 一对一关系，都被 `Claim.claimedValue` 这一个下游约束否掉。** 详见 3.2。

---

## 1. 第一轮意见的自我更正

第一轮意见（`case_truth_types_changes.md`）存在以下错误，本文与其冲突时以本文为准。

| # | 第一轮主张 | 更正 | 性质 |
|---|---|---|---|
| 1 | 在类型评审中给出 14 个 `*Message`、`apiTypes` 清单、`GameError → HttpError` 映射、路由名推导 | **删除**。已分离至 `api_design_draft.md`。类型评审不应包含落地实现，更不得反过来用接口建议约束类型 | 阶段错位 |
| 2 | `KeyDecoder.decodeKeyString.emap(...)`、`HttpError.unavailable / Unprocessable`、`deriveEncoder` 自动产出判别字段 | **作废**。这些写法未经样本核对，不可直接当作模板。样本 `HttpError` 只有 BadRequest / Unauthorized / Forbidden / NotFound / Conflict | 未核实即给代码 |
| 3 | 主张拆掉 `Topic:Fact` 一对一 | **撤回**。见 3.2 | 判断错误 |
| 4 | 把 `DiscoveryMethod` 归为"无载荷枚举" | **撤回**。`Fixed(label: Text)` 有载荷，含有载荷分支的枚举须整体走结构化 JSON | 分类错误 |
| 5 | 建议新增 `LoadCasePackageMessage` | **撤回**。`CaseRevisionTable` 已是公开方法，规则 10 允许直接调用；只做转发的消息是多余抽象 | 多余抽象 |
| 6 | 用 `Incident.affectedPeople` 生成指控候选；`AccusationOptions` 内带 `acceptedClueSets` | **撤回**。前者可能只含死者；后者直接泄题 | 安全错误 |
| 7 | 移动校验"方案 A：信任客户端位置" | **撤回**。v1.3 的"简化 AABB + 开放连接 + 入口落点 + 单步上限"更好，且明确声明了保证边界 | 降低保证 |
| 8 | 自造 `NonEmptyVector` 以"消除 cats 依赖"为由 | **撤回理由**。cats-effect 已引入 Cats 生态，重造集合不成立；v1.3 保留"项目统一集合实现"的表述即可 | 论据不成立 |
| 9 | "只加可选字段所以前向兼容" | **撤回**。旧解码器、默认值、枚举成员、reducer 行为都需版本策略 | 判断错误 |
| 10 | 建议新增"AI 调用监控页"补足页数 | **撤回**。不为凑页面新增技术页 | 方向不当 |
| 11 | 文件头路径 `../../../plan.md` | **更正为** `../../plan.md` | 路径错误 |

---

## 2. 第二轮：采纳项

### 2.1 P0（写第一行 Scala 之前必须定）

#### P0-1　`Connection` 的语义、准入条件与可引用性

分两部分：1.1 说明它**现在并没有枚举连接方式、必须守住这一点**；1.2 是它的五个真实缺口。

##### P0-1.1　现状正确：`Connection` 已经是"准入 + 代价"关系，不是"建筑构件"

`Connection`（119-123 行）的六个字段里，没有一个需要程序解释语义：

| 字段 | 程序如何使用 |
|---|---|
| `from` / `to` / `bidirectional` | 图的可达性判定 |
| `minimumTravelSeconds` | 数值比较 |
| `gate: Option[EntityId]` | 查 `WorldState.opened` / `unlocked` 两个集合 |
| `fromEntry` / `toEntry` | 距离计算 |

**"怎么过去"目前藏在三处，都不在类型里**：① 构件实体本身（窗、门、井盖、暗门都是 `Entity`，带 `background`）；② `Place.background` / `SemanticScene.overview` 的语义描述；③ `SceneNode` 的几何。所以"翻窗过去"和"钻暗道过去"在类型上是同一条边，差别在被引用的实体与叙述文本。

这条边界**必须显式守住**，因为它是这一层唯一容易崩掉的地方：

- **禁止**新增 `ConnectionKind` / `TraversalMode` 之类枚举。窗、门、暗道、梯子、竖井、通风管、翻墙、绳索、井口……无法穷尽，加进去就是再一次用枚举冒充覆盖现实。
- 该信息以 `traversalHint: Text`（见 1.3）与相关 `Entity.background` 承载，供叙述与 AI 使用，**永不参与判定**。
- §20.3 枚举理由表补一行，写明"`Connection` 不设 kind 的理由"。

判据（可复用于以后所有"要不要加枚举"的争论）：

> **程序要对它分支 → 类型／枚举；程序只搬运和比较 ID → 语义文本。**

##### P0-1.2　五个真实缺口

| # | 缺口 | 具体场景 | 后果 |
|---|---|---|---|
| a | **没有 `ConnectionId`** | 书房↔走廊之间既有一扇门又有一扇窗：两条边能同时存在，但**无法被引用**——`Condition`、`Operation`、`ConnectionView.exits`（`api_design_draft.md` 第 41 行只有 `to: PlaceId`）都区分不出"我说的是那扇窗"；叙述层也点不出来 | 同一对地点只能有一条边，或两条边互相混淆 |
| b | **没有"已发现"状态** | 暗道是"存在但玩家不知道"。`gate` 只能表达"被挡住"，表达不了"未被发现"。而 `exits` 要下发给客户端——**暗道会直接出现在出口列表里** | 隐藏通路无法表达。这是暗道的真正障碍，不是连接方式的问题 |
| c | **准入只有 `gate` 一个实体开合** | 表达不了"深夜才开""必须先撬开地板""只有某一方知道这条路"。这几条求值能力 `Condition`（436-444 行）本来就有，只是接不进来 | 现场解锁链写不出，只能退化成一堆 `Operation` |
| d | **入口没有半径** | `fromEntry` 是**一个点**，没有触发半径，玩家必须站在精确坐标才能触发。`Entry.World` 有 `radiusMeters`，这里却没有 | 翻窗这类交互几乎不可触发 |
| e | **`bidirectional` 只有两档** | 表达不了"下去容易上来难"的非对称代价 | 可用两条单向边绕过，但文档没写这个用法 |

注：**暗道本身完全可表达**——书房直通储藏室的暗道就是一条跳过走廊的**直接边**，图论上没有任何问题。缺的只是 (b) 的发现状态。

##### P0-1.3　修改

```scala
enum Condition:
  case All(items: Vector[Condition])
  case Any(items: NonEmptyVector[Condition])
  case ClueKnown(clue: ClueId)
  case FactKnown(fact: FactId)
  case OperationDone(operation: OperationId)
  case EventOccurred(event: EventId)
  case TimeReached(at: CaseTime)
  case PresentedTo(person: PersonId, clue: ClueId)
  case TopicAsked(person: PersonId, topic: TopicId)     // 见 P0-3
  case EntityOpened(entity: EntityId)                   // 新增
  case EntityUnlocked(entity: EntityId)                 // 新增

final case class Connection(
  id: ConnectionId,                                     // 新增
  from: PlaceId, to: PlaceId, bidirectional: Boolean,
  minimumTravelSeconds: Int,
  fromEntry: Vec3, toEntry: Vec3,
  fromRadiusMeters: Double, toRadiusMeters: Double,     // 新增
  gate: Option[EntityId],                               // 仅用于 UI 标注"这里有一扇窗 / 一道暗门"
  conditions: Vector[Condition],                        // 全部成立才可通行；空 = 无额外限制
  traversalHint: Text                                   // 语义：走过去 / 翻窗 / 钻暗道 / 爬梯
)
```

三个例子写成配置：

| 情形 | 配置 |
|---|---|
| 窗子 | 一条边 + `gate = window` + `conditions = [EntityOpened(window)]` |
| 暗道 | 一条直接边 + `conditions = [ClueKnown(tunnel_clue)]`（未发现时条件不成立，`exits` 中不出现） |
| 上锁的门 | `conditions = [EntityUnlocked(door), EntityOpened(door)]` |
| 只在夜间开启的暗门 | 再加 `TimeReached(at)`，全部使用既有 `Condition` 成员 |
| 上得去下不来 | 拆成两条单向边，各自不同 `minimumTravelSeconds`（写进 §4 用法说明） |

##### P0-1.4　准入语义收敛（替代原方案）

原方案是"在 §22.1 用散文写死 `unlocked` 与 `opened` 的先后"。加入 `EntityOpened` / `EntityUnlocked` 之后**散文规则不再需要**：

```
可通行 = Entry 定位成立（含 1.2-d 的半径）∧ ∀ conditions 成立
```

没有隐含顺序，也不存在"开着但锁着算不算合法"这种问题——作者写哪个条件就是哪个。`validateCase` 只需增加一条校验：

> **每个 `gate` 实体必须被该连接的某条 `EntityOpened` / `EntityUnlocked` 条件引用。** 否则就是"画着锁却能直接走过去"的编写错误。

这样 P0-1 从"要写一套实现约定"降级为"要加两个 `Condition` 分支和一条校验"。

##### P0-1.5　边界：`Connection` 是空间图，不是叙事图

`Connection` 只表达**位移可达**。若将来要表达"通过监控看到另一间房""用对讲机与另一处的人说话"这类**非位移连通**，不属于 `Connection`，而属于 `InquiryPoint` 的 `Entry`（`Entry.Report` 354 行、`Entry.Conversation` 355 行已覆盖）。**建议在 §4 明写这一句**，否则大概率会有人为了"信息可达"给 `Connection` 加变体，那时枚举就真来了。

**v1 明确不做**：`Condition` 增加"当前 actor 是某人"分支（让暗道只对特定人物开放）。它会牵出 `Observer` / `PlayerRole` 的求值上下文，属于嫌疑人模式那一档复杂度，推迟到 v2 与角色扮演一起考虑。

#### P0-2　调查入口可用性漏了"节点及父链当前可见"

**位置**：§9 第 382 行"半径 > 0，后端按接受的玩家位置、开放地点及入口条件验证访问"；§22.1 第 961 行只在讲**渲染下发**时提到"当前可见子节点不能绕过隐藏父级泄露"。

**问题**：抽屉 `initiallyVisible = false`，其子节点上的 `InquiryPoint` 在后端仍判为可访问。正常客户端不渲染它，但构造 `PointId` 的请求可以直接触达。v1.3 已经把"`InquiryPointView` 不下发 `topics`"当作防泄露手段（§23），说明这一类边界被认真对待，此处是真漏洞。

**修改**：在 §9 补一句判定规则，并列入 §20.2：

```
入口可访问 =
    该 Entry 所属节点及其全部祖先当前可见
  ∧ Entry 的访问条件成立（availableReports / materials / Notebook 线索已知 等）
  ∧ 玩家在半径内且 Place 已开放

Entry.World 不受节点可见性约束（只受 Place 与半径约束）。
```

**附带收益**：这条补上后，**"容器内物品"不需要新增类型**——见 3.3。

#### P0-3　`Condition` 缺"某人被问到某主题"，审讯的关键触发点无法表达

**位置**：`Condition`（436-444 行）只有 `ClueKnown` / `FactKnown` / `OperationDone` / `EventOccurred` / `TimeReached` / `PresentedTo`。

**问题**：审讯披露机制本身是完整的——`Entry.Conversation(person)`（355 行）+ 其 `InquiryPoint` + `DiscoveryRoute(condition, grants, reports)`（451-455 行）足以表达"出示某物后他会说某事"，`NpcReplyDraft.matchedRoutes`（617 行）批准后经 `Clue.result` 取得固定内容。

**能力缺口只在触发器上**：「**出示了照片他会松口**」能表达，「**你直接问到他妻子的事他才松口**」表达不了——而后者才是审讯的核心节奏。同时 `GameState` 也没有 `askedTopics` 状态可供求值。

**修改**：

```scala
enum Condition:
  case All(items: Vector[Condition])
  case Any(items: NonEmptyVector[Condition])
  case ClueKnown(clue: ClueId)
  case FactKnown(fact: FactId)
  case OperationDone(operation: OperationId)
  case EventOccurred(event: EventId)
  case TimeReached(at: CaseTime)
  case PresentedTo(person: PersonId, clue: ClueId)
  case TopicAsked(person: PersonId, topic: TopicId)      // 新增

// GameState（712-719 行）增字段，与 presented 同级
//   askedTopics: Map[PersonId, Set[TopicId]]

// SessionChange（731-746 行）增分支
//   case QuestionAsked(person: PersonId, topic: TopicId)
```

**不采纳**独立评审的配套提案 `DisclosureRule`，理由见 3.1；`AskedWithClue(topic, clue)` 也不必新增，用 `All(TopicAsked(p,t), PresentedTo(p,c))` 即可。

**注意**：`askedTopics` 放在 `GameState` 而非 `PlayerKnowledge`，与 `presented: Map[PersonId, Set[ClueId]]` 一致——它记录的是"某人被问过什么"，不是玩家自己的知识。

#### P0-4　`Unspecified` 与"可作为结论引用"在类型上脱节

**位置**：§20.2 第 915 行要求"Unspecified 不进入公开知识、线索、推理结论或参考答案"，但 `FactValue.ExistenceState(Unspecified)`（226 行）可以被下列位置合法引用：

```
FactKnown(fact: FactId)                  // 440 行
DeductionRule.conclusion: FactId         // 647 行
SolutionRequirement.conclusion: FactId   // 659 行
```

**问题**：这三处都是**作者手写的产出位置**（推理规则与答案键），把一条"未设定"事实当作结论或标准答案是发布级错误，目前只能靠散文拦截。这类"非法状态可表达"的问题，v1.3 自己在别处是用类型处理的（`EventId` / `BelievedEventId` 分离）。

**修改**：按同一先例引入一个只由校验派发的 ID 类型，**只用在两处作者产出位置**：

```scala
final case class AssertedFactId(value: String)

final case class DeductionRule(
  id: RuleId, premises: NonEmptyVector[Premise], conclusion: AssertedFactId)

final case class SolutionRequirement(
  question: QuestionId, incident: IncidentId, conclusion: AssertedFactId, label: Text,
  correctOptions: NonEmptyVector[OptionId],
  acceptedClueSets: NonEmptyVector[NonEmptyVector[ClueId]], weight: Int)
```

**刻意不铺开**：`Premise`、`PlayerKnowledge.facts`、`Clue.facts` 保持 `FactId`。这些是运行时集合，其不变式在**写入时**逐条过滤即可，不必改类型——过度铺开会让每次比较都要做类型转换，收益为负。这是有意的不对称，须在文档中写明理由。

#### P0-5　`FactsEstablished` 缺来源链，与 NPC 侧标准不一致

**位置**：`FactsEstablished(facts: Set[FactId])`（741 行）。对照 NPC 侧：`InformationAcquisition.sources: NonEmptyVector[InformationSource]`（535 行）、`PerspectiveChange.triggerRecords`（539 行）。

**问题**：玩家推导出的知识没有 derived-from 记录。§20.1 第 907 行说"查询玩家什么时候知道血迹是颜料"要查 `InformationAcquisition`，但推导结论**根本不经过它**。后果：复盘页（`plan.md` 页 14）无法回答"玩家凭什么知道凶手是谁"，只能靠重跑推导。

**修改**：

```scala
case FactsEstablished(facts: Set[FactId], viaRules: NonEmptyVector[RuleId])
```

`viaRules` 取非空，因为该变化只承载**推导**结果；线索直接确认的事实走 `Acquired` / `Clue.facts`（§16.2 规则 3 已如此区分）。

### 2.2 P1（v1 中应完成）

| # | 改动 | 位置 | 理由 |
|---|---|---|---|
| P1-1 | `Claim.speaker: Option[PersonId]` + 新增 `speakerLabel: Text` | 240-243 | `InitialReport.author: Text`（316 行）与 `Claim.speaker: PersonId` 不匹配：机构报告（警方现场勘查、法医中心意见）无法作为 Claim 主体。更关键的是**匿名信 / 伪造署名**是推理体裁的常见要素（`moonlight_sonata.md` 的核心设定就是伪造署名的委托），强制每条说法都有真实说话人会逼作者造假 |
| P1-2 | `DiscoveryRoute` 增 `fallbackLine: Option[Text]` | 451-455 | 审讯侧缺固定回退句，与调查侧不对称：`InquiryPoint.fallbackFinding`（359 行）已解决调查回退，但 §14 第 636 行要求"不通过时回退到获准内容的固定答复"、§25 第 1006 行要求"预制表现"，**都没有字段可存那句话**。`Clue.result` 只在路线确实授予线索时才有文本 |
| P1-3 | `MovementPolicy` 从 `SceneSpec` 移出 | 68、363 | 它在 `CasePackage` 里，而 `CasePackage` 是发布后不可改、参与 `sceneFingerprint` 的定稿内容。`maximumStepMeters` 是反作弊阈值，不是案情：改一次上限就要重发案件修订，还可能触发场景重验。应落到 `services/system` 的运行配置 |
| P1-4 | `Place.walkable: Option[NonEmptyVector[SceneBounds]]` | 117 | 现为 `Vector`，允许"可探索地点却是空列表"的非法状态，与文档用 `NonEmptyVector` 表达非空的惯例不一致。`None` 即非探索地点 |
| P1-5 | `SceneReview.checks: Map[SceneRequirementId, RequirementReview]` | 376 | §9.1 第 394 行写"每条要求**须有且仅有一项**当前结论"，但 `Vector` 允许同一 `requirement` 出现两条、形成两个结论 |
| P1-6 | `PlayerKnowledge` 增 `readReports: Set[ReportId]`、`revealedPeople: Set[PersonId]` | 708-711 | ①§17 第 833 行明言"可打开报告不等于已经读过"，但只有 `availableReports` 一个集合，页 07 无法区分"可打开"与"已读"；②§23 第 987 行要求过滤未公开身份，而 `PublicBackground.persons` 全量公开、没有玩家侧解锁状态，`plan.md` 5.2 明确要"根据线索解锁嫌疑人信息" |
| P1-7 | 删除 `Subject.Clues(ids: NonEmptyVector[ClueId])` | 218 | `Fact` 属 `CaseTruth`（客观真相层），`Clue` 属调查层（玩法粒度）。让真相以"线索组"为主体，等于承认真相依赖于作者的线索划分，调整线索就要改真相结构。而"这几条线索共同说明什么"本就是 `DeductionRule` 的职责，属功能重复的逃生口。`Entry.Notebook(clues)` 保留，那是界面入口，与 `Subject` 无关 |
| P1-8 | `Relationship` 明确降级为"制作提示，不是真相" | 132-135 | 与 `FactValue.Relation(left, relation, right)`（229 行）双重表达同一关系（"A 是 B 的妻子"可同时存在于两处）。而 `Claim` 必须在 `FactValue` 层面声称关系，所以**真相侧只能留 `FactValue.Relation`**。`Relationship` 保留但须声明"不得作为结案依据，真实关系以 Fact 为准"，或改为从 Fact 派生 |
| P1-9 | `InvestigationSpec` 增 `requiredIncidents: NonEmptyVector[IncidentId]` | 811-817 | §15 第 682 行要求"整案完成需要所有**必须处理的** Incident 分别通过"，但"必须处理"没有字段，只能从 `solution` 里出现过的 `IncidentId` 隐式推断——推断规则没写就是没定义 |
| P1-10 | 明确 `Operation.effects` 的执行顺序语义 | 489-492、§11 第 510 行 | 未说明 `AdvanceTime` 与 `WorldEffect` 的先后。建议定为："按 `effects` 声明顺序应用；`AdvanceTime` 触发的途中预设事件在该项所在位置展开；`AdvanceTime` 之后的 effect 在推进后的剧情时刻生效" |

### 2.3 P2（可后补）

| # | 改动 | 说明 |
|---|---|---|
| P2-1 | `Topic.when: Option[TimeWindow]` | 身份类主题（"A 是否是 B 的儿子"）没有时间属性，必填会逼作者塞一个超宽窗。同时须补一句说明：**`Topic.when` 是"该问题的有效时段"，`FactValue.Time` 是"答案本身是个时间"，二者是两级而非重复**——目前文档没写，容易误读 |
| P2-2 | `Matrix4` 的"16 个有限值、列主序"列入 §20.2 清单 | 66 行定义、963 行只在散文说明，发布检查清单（911 行起）未收录 |
| P2-3 | `InvestigationRecord.route` / `operation` 的"最多一个有值"改为三态 ADT | 579 行用注释表达约束；文档在别处是用 `NonEmptyVector` 而非注释保证非空的 |
| P2-4 | `SolutionRequirement.label` 与 `AccusationQuestion.label` 去重 | 答案键里再存一份问题标签，改问题标签忘改答案键会导致 UI 与复盘显示不一致。UI 标签应从 `AccusationQuestion` 取 |
| P2-5 | 三条手法相关校验规则写入 §20.2 | 见 4.3，成本近零，堵住"玩家改表与作者设定不一致"的破窗 |
| P2-6 | `Absent` 事实的检查范围规则 | "确认缺失须限定检查时间与范围"（AGENTS.md）在类型上只有 `Topic.when` + 自由文本 `boundary`。建议 §20.2 要求：每条 `Existence.Absent` 事实的 `Topic.when` 必须是闭区间且不晚于开局或该次检查时刻 |
| P2-7 | `AccusationOption.visibleWhen` 的可用范围限制 | 不能用它隐藏"合理的错误选项"；否则玩家会觉得被坑。应限定为叙事性解锁（如"该问题在取得某份报告后出现"），且 `validateCase` 检查每个问题的可见选项数 ≥ `minimumSelections` |

---

## 3. 第二轮：驳回项

清楚记录为什么不做，避免下一轮重复提出。

### 3.1 驳回：新增 `DisclosureRule` 类型

**提案摘要**：新增 `DisclosureTrigger`（`Asked` / `Presented` / `AskedWithClue`）、`DisclosureGrant`、`DisclosureRule(id, person, trigger, condition, grants, stance, perspectiveHint, fallbackReply)`，理由是"没有任何类型承载谁、被问什么、被出示什么、在什么条件下、可以说什么"。

**驳回理由**：

1. **"没有 grants/条件字段"不成立。** `disclosureRoutes: Vector[CandidateRoute]`（613 行）的 `CandidateRoute.id: RouteId` 指向 `DiscoveryRoute`（451 行），后者带 `point` / `condition` / `grants` / `reports`。披露内容与条件都在被引用的路由上，只是不在包装层上——`InvestigationAiContext.candidates`（597 行）用的是同一机制。
2. **"固定内容"也有。** 批准后 `grants` 走 `Clue.result: FindingContent`。不固定的是**措辞**，而这是设计意图（§14 第 636 行）。
3. **`stance` 已有归属。** "承认 / 回避 / 坚持原说法"属于角色固定内容，已在 `PersonPerspective.disclosureStrategy`（291 行）与 `intentions`（291 行）表达，不必为每条披露规则复制一份。
4. **真正缺的只有两样**：`TopicAsked` 触发器（P0-3）与 `fallbackReply`（P1-2）。二者都能加在既有类型上，成本远低于新造一个平行机制。

**采纳部分**：`Asked` 触发器的需求成立，见 P0-3；`fallbackReply` 成立，见 P1-2（位置取 `DiscoveryRoute`，不另设类型）。

### 3.2 驳回：拆掉 `Topic:Fact` 一对一

**提案摘要**：`Fact` 增 `subject` + `during: Option[TimeWindow]`，`topics: Set[TopicId]` 改多对多；`Topic` 降级为可复用的提问模板；冲突检测交给 `validateCase`。

**驳回理由（关键）：`Claim.claimedValue` 需要唯一锚点。**

`Claim(id, speaker, topic: TopicId, claimedValue: FactValue, wording, intention)`（240-243 行）的语义是"某人就某个主题给了一个值"。**只有主题↔真值一对一，`claimedValue` 才良定义**：

- 若一个 `Topic` 下有多条 `Fact`，"他说谎了"这句话指向哪一条？
- 若把时间移到 `Fact.during`，`Claim` 必须自己也带 `during` 才能对齐——时间窗从 1 处变成 3 处（`Fact`、`Claim`、`Topic` 残留范围）；
- 冲突检测（同主题同时间窗不得有不同值）的工作量一分没减，只是挪到了 `validateCase`。

**代价确实存在**：4 嫌疑人 × 10 余线索的规模下，`Topic` 与 `Fact` 各一份 ID 会让手写 `CasePackage` 的边际成本上升。但解法在**编写工具层**（案件模板、`Topic` 复用清单、生成脚本），不在类型层。为摊销一点 ID 成本去拆掉 `Claim` 的语义基础，是净亏。

**方法论提示**：本轮两份提案都从"编写省事"出发提出过这个改动，也都被同一个下游约束否掉。以后再动 `Topic` / `Fact` / `Claim` 三者关系，先检查 `Claim.claimedValue` 是否还能保持唯一锚点。

### 3.3 驳回：`FactValue.EntityState(during, state)`

**提案摘要**：手法核心命题（钟被拨到 21:10、录音机设为延时启动）塞进 `AuthoredDetail` 后，`SolutionRequirement.conclusion` 与 `DeductionRule` 无法引用，因此手法案的关键命题失去程序可校验性。

**驳回理由（前提取错）：判定过程中程序从不解释 `FactValue`。**

§15 第 680、682 行写明：结案判定是**固定点迭代 + ID 组合判定**。`evaluateAccusation` 比较的是 `FactId`、`OptionId`、`ClueId` 集合，从不读取 `FactValue` 里的字符串。所以一条 `AuthoredDetail("挂钟被拨到21:10")` 作为 `conclusion: FactId` **完全合法、完全可判定**。

`EntityState` 唯一收益是给人看时更结构化，而 §6 第 249 行已经给过答案："AuthoredDetail 正常承载开放事实，不意味着程序能验证全部语义"。为纯可读性收益扩大一个声称封闭的枚举，与 §20.3 的自我要求相反。

**若确实需要结构化**，正确位置不是 `FactValue`，而是给 `AuthoredDetail` 加一个可选作者标签（`AuthoredDetail(detail, label: Option[Text])`），不引入新语义分支。

### 3.4 驳回：`EntityLocation` 容器与携带

**提案摘要**：`WorldState.entityPlaces: Map[EntityId, PlaceId]` 只能到房间，"抽屉里的钥匙"无法表达，建议改 `EntityLocation` 三态（`InPlace` / `Inside` / `Carried`）。

**驳回理由**：**P0-2 修好后链条已经完整**：

- `SceneNode.parent`（345 行）→ 信是抽屉节点的子节点；
- 抽屉初始 `initiallyVisible = false`；
- 打开抽屉 = `Operation` + `WorldEffect.SetNodeVisible`；
- 入口可用性补上"节点及父链可见"（P0-2）之后，抽屉关着时信件上的 `InquiryPoint` 后端也拒绝。

"钥匙在抽屉里"作为**客观事实**已由 `FactValue.Relation(Entity(key), "位于", Entity(drawer))` 或 `AuthoredDetail` 覆盖。

成本对比：提案要新增三态联合 + Inside 链无环校验 + 容器同 Place 校验；修 P0-2 只要一句话。且 v1.3 已确定不做工具背包（§10.1），`Carried` 没有消费方。

**若首版场景确实出现"暗格套暗格"，再按需引入**——但那时应优先用 `Fact` 表达包含关系，而不是给 `WorldState` 加位置代数。

### 3.5 驳回：事件时间区间化（`TimePrecision`）

**提案摘要**：`occurredEvents: Map[EventId, CaseTime]`、`CaseEventOccurred(event, at)`、`ScheduledOccurrence.at` 把区间事件压成点，违反"不补造精确时刻"。

**驳回理由**：**真相层已经是区间。**

`EventAccount.when: Option[TimeWindow]`（174 行）就是区间，"21:00 至 21:20 之间倒地"本来就写在这里。被点刻化的是**运行时触发标记**——"本局时钟走到哪个点，该事件被登记为已发生"，而 §17 第 832 行的校验已把它钉在 `when` 之内。

运行时触发必须有一个确定时刻，否则 `ScheduledOccurrence` 无法按序展开。把 `at` 改成区间，等于要求引擎在每个时刻都重新判断"是否已落入某区间"，与 §16.2 的确定性重放要求冲突。

**采纳部分**：字段名容易误读，建议改名为 `occurredAt` 或加注释，说明它是"登记时刻"而非"发生时刻"。这是命名问题，不是类型问题。

### 3.6 驳回：`PerspectiveChange` 改为 delta

**提案摘要**：`PerspectiveChange` 存整份 before/after `PersonPerspective`，叠加 `NpcState.perspective` 与 `Snapshot.state` 形成三重复制，写入放大、快照膨胀。

**驳回理由（规模上不成立）**：v1 是 4 个 NPC × 十几条提交 × 一份 `PersonPerspective`（主要由 `Set[FactId]` 与少量文本构成），总量在几百 KB 量级。改 delta 会让"某人在序号 N 的视角"必须重放才能得到，而 full snapshot 恰好支持**免重放查询**——这是 §20.1 审计查询（"弟弟 09:20 之前相信什么"）的直接依赖。

**采纳部分（换个位置处理）**：真实风险在"单个 commit 的 jsonb 体积随 `believedEvents` 增长"。建议加一条上限（单次 `PerspectiveChange` 的 `believedEvents` 条目上限），而不是改结构。

### 3.7 驳回：把下列内容判为"不属于领域文档"

**提案判据**："领域契约回答什么概念存在、什么不变式成立；目录归属、表结构、编解码器、验收流水、函数清单回答怎么落地。"

**本文判据**：**删掉它，是否有一条不变式失去书面依据。**

按本文判据逐项裁定：

| 被提案判为越界的内容 | 裁定 | 理由 |
|---|---|---|
| 服务归属一句（21 行） | **同意精简**，非越界 | 归属已迁到 `api_design_draft.md` §3.1，此处可改为一句指向 |
| "一类型一文件 / 伴生对象放编解码器"（25 行） | **驳回** | 交付形态约定，规则 14/15 的落点。一句话成本，删了丢约束 |
| 存储集合表（§20.1，895-905） | **驳回** | 它讲的不是表结构，而是**真值归属**——"日志是唯一真值、记录表只是索引投影"。§16.2 规则 8 以此为依据 |
| `AiCallRecord`（622-627） | **驳回**（字段可议） | 承载"失败调用不占提交序号、不产生部分状态、秘密不外泄"。`model` / `promptVersion` / `inputDigest` 三个字段偏运维，可标注为运维字段，但类型本身是领域约束 |
| `RequestReceipt`（776-784） | **驳回** | 承载"同 ID 不同载荷必须拒绝、Pending 不得二次发起、崩溃后不重复执行" |
| 纯函数职责表（853-867） | **驳回** | 承载"哪些计算必须确定性、可重放、不得进 IO 或模型调用"。这是领域约束，不是实现清单 |
| `SceneReview` + §9.1 验收流程 | **驳回**（流程文字可精简） | `SceneReview` 参与**发布门槛**："有 Pending / Failed / 缺项即不得发布"是领域规则，不是流水记账 |
| `SceneAuthoringInput`（970-977） | **同意降级**为"制作阶段接口"小节 | 它确实不进 `CasePackage`，当前位置可接受，但不该与运行时类型混排 |
| 范围裁剪记录（949、999-1001 行） | **驳回** | §25 写的正是"**尚未确认**"清单，其作用是防止把裁剪当成结论。删掉才危险 |
| `Matrix4` + 列主序 + AABB 白名单（66、963-965） | **部分同意** | `Matrix4` 在领域类型里没有任何用户（唯一使用方是 `api_design_draft.md` 第 35 行的 `SceneNodeView`）。建议连同列主序说明整体移到 API 草案，领域侧只保留 `placeMatrixOf(node): Matrix4` 的函数签名与父链约定 |

**分歧的核心**：提案把"不是运行时类型"等同于"不属于领域文档"。但制作期契约、发布门槛、审计记录、确定性要求，都是同一份契约在不同时间尺度上的组成部分。真正该出界的只有一类：**能被领域类型推出的传输形状**（`PlayerSceneView`、`CaseSummary`）——而 v1.3 已经移走了。

---

## 4. 值得直接采纳的三组补充

### 4.1 手法案的三条硬规则（写入 §20.2）

1. **机关只做只读对象。** 检查与演示结果一律来自 `Clue` + `Fixed` 路线；`Operation` 只能触发预制演出，不得产生读数。
2. **`Operation.condition` 不得引用装置状态；`WorldEffect` 不得改写带 `EntityState` 类事实的实体状态。** 否则会出现"玩家操作与作者设定不一致"的破窗。
3. **不做时序算术谓词。** 不给 `FactValue` / `Premise` 加 `EarlierThan` 之类可计算谓词；"死亡先于录音播放"写成一条被 `DeductionRule` 前提支撑的结论 `Fact`。代价最小且可人工审核。

### 4.2 `Absent` 事实的范围要求

见 P2-6。核心是：`boundary` 是自由文本，不构成可校验范围；须借助 `Topic.when` 闭区间 + 检查时刻约束把它钉住。

### 4.3 `Operation.effects` 的顺序定义

见 P1-10。三条规则写清即可，不新增类型。

---

## 5. v1.4 变更总表

「来源」列：**自**=本轮自行发现；**外**=独立评审提出；**外·改**=独立评审提出但改修法。

### P0

| # | 类型 | 动作 | 位置 | 来源 |
|---|---|---|---|---|
| 1a | `ConnectionId` | 新增 | §2 ID 清单、119-123 | 自 |
| 1b | `Condition` | 增 `EntityOpened(entity)`、`EntityUnlocked(entity)` | 436-444 | 自 |
| 1c | `Connection` | 增 `id`、`conditions`、`fromRadiusMeters` / `toRadiusMeters`、`traversalHint` | 119-123 | 自 |
| 1d | §4 / §22.1 / §20.3 | 禁止 `ConnectionKind`；写明空间图边界；`gate` 必须被条件引用 | 110、§22.1、928-945 | 自 |
| 1e | §4 | 补"单向边表达非对称代价"的用法说明 | 155 行附近 | 自 |
| 2 | §9 入口访问条件 | 补"节点及全部祖先可见" | 382 行 | 自 |
| 3 | `Condition` | 增 `TopicAsked(person, topic)` | 436-444 | 外·改 |
| 3b | `GameState` | 增 `askedTopics: Map[PersonId, Set[TopicId]]` | 712-719 | 外·改 |
| 3c | `SessionChange` | 增 `QuestionAsked(person, topic)` | 731-746 | 外·改 |
| 4 | `AssertedFactId` | 新增；用于 `DeductionRule.conclusion`、`SolutionRequirement.conclusion` | 646-662 | 外 |
| 5 | `SessionChange.FactsEstablished` | 增 `viaRules: NonEmptyVector[RuleId]` | 741 | 外 |

### P1

| # | 类型 | 动作 | 位置 | 来源 |
|---|---|---|---|---|
| 6 | `Claim` | `speaker: Option[PersonId]` + `speakerLabel: Text` | 240-243 | 外 |
| 7 | `DiscoveryRoute` | 增 `fallbackLine: Option[Text]` | 451-455 | 双方 |
| 8 | `MovementPolicy` | 从 `SceneSpec` 移除，落 `services/system` 运行配置 | 68、363 | 自 |
| 9 | `Place.walkable` | 改 `Option[NonEmptyVector[SceneBounds]]` | 117 | 自 |
| 10 | `SceneReview.checks` | 改 `Map[SceneRequirementId, RequirementReview]` | 376 | 自 |
| 11 | `PlayerKnowledge` | 增 `readReports`、`revealedPeople` | 708-711 | 外 |
| 12 | `Subject` | 删 `Clues` 分支 | 218 | 外 |
| 13 | `Relationship` | 注明"制作提示，非真相" | 132-135 | 外 |
| 14 | `InvestigationSpec` | 增 `requiredIncidents` | 811-817 | 外 |
| 15 | §11 | 明确 `Operation.effects` 顺序 | 489-492、510 | 外 |

### P2

`Topic.when → Option` 与两级时间说明 · `Matrix4` 不变量入 §20.2（或整体移入 API 草案）· `InvestigationRecord` 三态化 · `SolutionRequirement.label` 去重 · 手法三规则 · `Absent` 范围规则 · `AccusationOption.visibleWhen` 限制

### 明确不做

`DisclosureRule` · 拆 `Topic:Fact` · `FactValue.EntityState` / `Fact` 迁 `subject`+`during` · `EntityLocation` 容器三态 · `TimePrecision` 事件区间化 · `PerspectiveChange` 改 delta · `PlayerKnowledge` 增 `inspectedPoints` · 指控草稿类型 · 嫌疑人模式相关类型（v2）· 为凑页面新增技术监控页 · **`ConnectionKind` / `TraversalMode` 等连接方式枚举** · `Condition` 的"当前 actor 是谁"分支（v1）

---

## 6. 待作者拍板

1. ~~空间通行是两级还是一级？~~ **已由 P0-1.4 消解**：`gate` 的判定提升为 `Condition` 分支后不再需要顺序约定。**作者只需决定：玩法上"撬锁"是否等于"开门"**——若等于，门的配置写一条 `EntityUnlocked(door)` 即可；若不等，写两条。这是内容配置问题，不是类型问题。
2. **`TopicAsked` 是否需要"追问次数"？** 建议不要——用多条 `DiscoveryRoute` 逐级加严条件即可；引入计数会让 `Condition` 求值带不可重放状态。
3. **`AssertedFactId` 的铺开范围**是否接受本文的"只用在两处作者产出位置"？若要覆盖 `Premise` 与 `PlayerKnowledge`，改动面扩大，需评估进度。
4. **`Relationship` 是收紧还是保留双份？** 收紧 = 只留 `FactValue.Relation`，关系从 Fact 派生；保留 = 两处并存 + `validateCase` 交叉校验。
5. **`movement` 配置放哪里？** `services/system` 的部署配置，还是 `GameSession` 级别的可调参数（影响调试与演示）。
6. **暗道是否需要"只有特定人物能走"？** 本文建议 v1 不做（见 P0-1.5）。若做，需先定 `Condition` 的求值上下文如何拿到当前 actor。

---

## 7. 未解决的核实问题

1. **`../cases/murder_at_the_vicarage.md` 的回忆级细节未核对。** 撰写环境无外网（到 `gutenberg.org`、`agathachristie.com` 的请求均失败），该文件已用信度标注与"待核实清单"自我约束，但**在核对前只能作结构参照，不得进入 `CaseTruth`**。第一轮文件 §8.3 已指出其中存在错误条目。
2. **样本 `HttpError`、`KeyDecoder`、路由名推导的实际形态**须在实现前对当前版本逐一核对，不可沿用任何未经验证的代码模板（见第 1 节第 2 项）。
3. **AI 调用的长事务问题**（`api_design_draft.md` §3.2 末段）优先级高于本文件全部 P2，且不属于领域类型范围，须在实现前单独设计。

---

## 8. 开工界线与冻结清单

### 8.1 冻结线

类型设计在本轮之后**冻结**。只允许两类回改：

1. **编译期撞墙**：某条第 2 天必须跑通的行为，现有类型表达不出来。
2. **垂直切片走不通**：见 8.2 那条路径卡住，且卡点确实是类型缺失而非实现问题。

除此之外，任何"再加一个类型会更完整"的想法一律记入 §5 的 P2，**不动手**。

理由：v1.3 已有 153 个类型。P1/P2 的共同特征是**局部可加**——加一个字段、加一个 `Condition` 分支、加一个 `Map`，都不改变既有日志格式、判定语义与重放规则。这类改动在实现现场按需长出来的成本，与现在做几乎相同；而**在开工前做，收益为零、代价是每天推迟**。

### 8.2 第一刀：垂直切片

不要"先写完 system 再写 cases 再写 games"。选一条最窄的完整路径，让类型按需长出来：

> **进入案件 → 加载书房 → 检查书桌抽屉 → 得到一条线索 → 调查日志更新**

这一刀真正需要的类型：

| 模块 | 需要的类型 | 可先留空 / 不建的部分 |
|---|---|---|
| system | `Text`、ID 值类型、`Vec3`／`Transform`、codec、错误、事务与连接 | — |
| cases | `CasePackage`、`Place`、`Entity`、`Connection`、`SceneNode`、`InquiryPoint`、`Entry`、`Clue`、`FindingContent`、`DiscoveryRoute`、`Condition`（只需 `All` / `ClueKnown` / `OperationDone` / `EntityOpened`） | `CaseTruth`、`WorldSpec` 其余部分、`SceneRequirement`、`SceneReview`、`InitialReport`、`DeductionRule`、`SolutionRequirement`、`Accusation*` 全部填空 `Vector` 即可通过编译 |
| games | `GameSession`、`GameState`、`WorldState`、`PlayerKnowledge`、`InvestigationRecord`、`SessionCommit`、`SessionChange`（只需 `WorldChanged` / `Investigated` / `PlayerMoved`） | `Snapshot` 可用"每次从头重放"暂代；AI 相关类型一个都不建 |

**这一刀完全不碰**：`Event`、`Topic`、`Fact`、`Person`、`PersonPerspective`、`Incident`、Motive、全部 AI 上下文／草稿类型、`ScheduledOccurrence`、`AdvanceTime`。

**由此可以确认：约九成的类型设计并不阻塞开工。真正的前置只有 system 那一层值类型与 codec。**

配套约束：这一刀使用**玩具案件**（2 个地点、3 个物体、1 条线索、0 个嫌疑人），**不要现在就去写正式案情**。正式案情等这一刀跑通、且 `CasePackage` 的字段稳定之后再写，否则案情会被字段改动反复推翻。

### 8.3 P1/P2 的解锁条件

| 改动 | 什么时候必须回来做 |
|---|---|
| P1-1 `Claim.speaker` 可选 | 开始写第一条预设说法时 |
| P1-2 `fallbackLine` | 第一次接 AI 审讯、需要模型失败回退时 |
| P1-3 `MovementPolicy` 移出 | 第一次调移动阈值时（越早越省事，可在切片阶段顺手做） |
| P1-4 `Place.walkable` 改 `Option` | 写第二个地点时 |
| P1-5 `SceneReview.checks` 改 `Map` | 第一次做场景验收时 |
| P1-6 `readReports` / `revealedPeople` | 做页 07 调查日志与页 08 人物档案时 |
| P1-7 删 `Subject.Clues` | 写第一条 `Fact` 时（顺手删掉即可） |
| P1-8 `Relationship` 降级 | 写第一节人物关系时 |
| P1-9 `requiredIncidents` | 做结案判定时 |
| P1-10 `Operation.effects` 顺序 | 写第一个 `Operation` 时 |
| P2 全部 | 出现具体需要时再说，不必预设时间点 |

### 8.4 "何时该停止设计"的可执行判据

以后每次想再改类型，先过这三问，任何一问命中就冻结：

1. **它改变既有日志格式或判定语义吗？** 不改变 → 冻结，实现现场加。
2. **垂直切片能暴露它的缺失吗？** 暴露不出来 → 说明当前用不到，冻结。
3. **它只是让"编写案件更省事"吗？** 是 → 冻结，等真写案件时再判断。

这三问也是本轮 `Topic:Fact` 之争的结论：两份提案都是从第 3 问出发的，而被第 1 问（`Claim` 锚点属于判定语义）否掉。


## 9. 再复核：采纳方向、修法与开工门槛（2026-10-27）

本节是针对本文及当前 v1.3 的再次评审，不自动修改领域类型，不代表用户已确认冻结或授权开始编码。结论：停止全量追求完整、转向小切片的方向正确；不能原样执行“先做全部五项 P0、其余全部冻结”。优先级应按下一条实际路径及信息安全风险确定。

### 9.1 五项 P0 的再裁定

| 项目 | 再裁定 | 必须修正的地方 |
|---|---|---|
| P0-1 通路 | 采纳稳定 ID、通用条件和入口容差；隐藏性与通行性须分开 | 已知但上锁的门应显示为不可通行，未发现暗道才不显示。单个 conditions 同时控制显示与通行无法表达这两种状态；可用独立 revealWhen／traverseWhen 条件，不必新增 discoveredConnections 集合。平行边的 ID 必须贯穿操作选择与移动校验，不能新增 ID 后仍仅按目标 Place 选边。 |
| P0-2 入口访问 | 采纳节点及父链有效可见性校验 | 按 Entry 变体分别判断，不能要求报告、对话和日志入口都处于世界半径内。可见性不等于完整物理遮挡。抽屉外壳应保持可见，将内部内容节点隐藏，否则“检查抽屉”入口可能一起消失。 |
| P0-3 询问主题 | 需求成立，现方案未闭合 | TopicAsked 永久集合表示“曾经问过”，不是“本轮正在问”；问过妻子后，再问天气不应自动触发妻子路线。应先区分本轮已批准语义匹配与历史条件；自由文本识别 Topic 的候选范围、校验、何时写日志也要明确。不能先按 TopicAsked 筛掉候选，再让 AI 从剩余候选识别 Topic，形成闭环。无需为此立即新建 DisclosureRule。 |
| P0-4 AssertedFactId | 暂不列为开工前必做 | 新建公开 String 包装不保证它指向非 Unspecified 的 Fact；仍需引用解析与发布验证。它与 EventId／BelievedEventId 的区别在于：前者是同一 Fact 的受验证子集，后者引用不同对象集合。先集中校验所有知识／结论／答案引用即可；若采用“校验派发”，要落实受控构造及解码边界，不能仅凭改名宣称非法状态不可表达。 |
| P0-5 推导来源 | 采纳记录来源的目标，否决当前字段作为完整来源链 | facts 集合与 viaRules 集合没有一一关联；多个事实、多条规则与级联推导时，无法知道某个结论由哪条规则产生。至少记录逐条 derivation（结论、RuleId、实际前提引用），与同提交顺序对齐；案件修订固定，可据 RuleId 读取静态前提配置，无需复制全部文本。空新增结论不生成该变化。此项在首次实现推导／复盘前完成，不阻塞无推导的首切片。 |

P0-1 另有三处过强或错误的表述：

- gate 若定义为纯 UI 关联，就不应强制每个 gate 必须出现在 EntityOpened／EntityUnlocked 条件里；可通行的拱门、坏锁、通过 OperationDone 解锁的入口都是反例。也不能仅检查“引用过”：Any 中另一个恒真分支会绕过条件。需要的是明确准入语义，不是表面引用校验。
- TimeReached 只有下界，不能独自表达“只在夜间开启”或到点关闭。当前切片不用时就不扩展；文档例子应写成“到某时刻后开启”，不声称已有时间窗口能力。
- 单向不可返回只需一条单向边；双向但不同代价才是两条反向单向边。入口容差有必要，但使用全局容差或每边半径是实现选择，不是所有案件都需的新领域概念。

### 9.2 其他意见中应保留与应改写的部分

可保留：不再平行建立 DisclosureRule；保留 Topic／Fact／Claim 的现有锚定关系；开放手法继续用 AuthoredDetail；目前不为容量优化改 PerspectiveChange delta；暂不建立完整容器／携带系统。它们均不阻止首切片。

需要改写：

1. **P1 实为十项，不是摘要的九项。** 只影响清单准确性。
2. **Claim 的真实来源与声称署名应分开。** Option[PersonId] + label 可处理部分匿名情况，但“真实作者已知、文书伪署他人”不能只靠改变 speaker 表达；在首次制作此类说法时明确两种语义，不为通用组织体系提前扩张。
3. **审讯固定回退需求成立，但字段位置不必定在 Route。** 路线未匹配或不允许时可能根本没有可选 Route；入口级 fallbackFinding 已可承载文本，应先定义复用方式，不能为回退而读取未获准路线的秘密文本。
4. **Map 不自动消除所有验收问题。** 仍要验证要求全集覆盖、无未知键、key 与 value.requirement 一致；解析时还不能默默覆盖重复输入。Vector 加唯一性校验同样可用，不是必改结构。
5. **Relationship 重复真值问题成立，但不应直接把所有关系降为美术提示。** 可规定它是客观关系目录，参与推理时由 Fact 锚定／引用，或明确从 Fact 派生；不要丢掉当前事实引用之外的真实背景。
6. **事件实际时间与引擎激活时间必须区分，不能混称登记时刻。** 当前 occurredEvents 与计划 at 仍被要求落在 Event.account.when 内；这不支持“引擎任意晚于事件登记”的解释。历史仅知区间时，不应伪造一个点后再在复盘宣称精确发生时间。首切片不使用事件即可延后修正；不需要立即引入 TimePrecision。区间本身也不妨碍确定性，关键是引擎采用固定激活规则。
7. **追问计数不天然破坏重放。** 经日志确定性累计就可重放；目前可以因不需要而不做。重复相同 Topic 不增加集合元素，多条逐级 Route 也不能自动替代计数。
8. **第 4.1 节手法规则过度收紧。** 禁止 Operation.condition 读取装置状态，会直接禁止刚建议的 EntityOpened／EntityUnlocked 等正常条件。禁止 Operation 产生任何读数也超出已确认方向。正确约束是：操作只能触发已登记、可校验的状态变化及固定结果，不能修改历史；受操作影响的当前状态不能冒充固定历史事实。原文 EntityState 已被否决，不能再用它作为可执行校验依据。
9. **Absent 的时间校验不等于范围校验。** 可以用 Topic 的时间及语义 boundary 限定调查结论，空间／采样范围仍需作者与语义审查；不能从闭区间条件推出“缺失范围已完全机器校验”。
10. **MovementPolicy 移到运行配置可以延后，但不是无语义影响。** 它改变哪些移动被接受；需区分历史重放（只应用已批准移动）与新请求校验（使用当前策略），不必为了调阈值重验全部视觉资产。

### 9.3 停止评审的界线：方向赞同，原判据不宜照用

原第 8 节有三项实质问题：

- “P1/P2 都不改变日志格式／判定语义”与第一轮已撤回的兼容性判断重复。改 Claim、增加知识状态、改变 effects 顺序都可能影响持久化或判定。应改为：首次持久化前修改较便宜；持久化后任何结构／语义变更都检查版本及迁移。
- “只允许编译失败／首切片失败回改”漏掉权限越界、历史被改写、不可恢复的存储约定；这些即使编译通过也必须允许修复。也不应只因某问题不在第一条切片中就永久冻结。
- “任何一问命中就冻结”会把收益明确且几乎零成本的修正也挡住；是否影响日志不是判断该不该做的唯一标准。“编写更省事”也可能直接决定课程作业能否按时完成。

建议把“类型冻结”改为**阶段基线**：不再做全量类型扩张；当且仅当下一项已排定能力无法正确表达，或发现信息安全／历史一致性问题时修改必要部分。每次仅说明一个具体失败场景、最小修法、影响的持久化格式；其余记待办。进入实现后，用实际问题触发局部复核，不另开无限的全面评审轮次。

### 9.4 第一条切片可行，但原清单不能原样执行

原方向“进入案件 → 场景 → 抽屉 → 线索 → 日志”合理；补充“刷新／重新加载后知识仍一致”作为完成证据。玩具夹具不是正式案情，不必先写整案。

原清单有可直接从当前类型反证的遗漏：

- Clue.facts、InvestigationSpec.solution 等是 NonEmptyVector；CaseTruth 与 WorldSpec 是聚合对象，不能全部用空 Vector 替代。选择：提供最小合法 Fact／Topic／答案配置作为夹具，或明确采用只支持调查的阶段包，并设计后续映射；不能声称不调整契约就能全填空编译。
- 得到线索并更新知识必须经过 Acquired／InformationAcquisition／LearnedInformation；只有 Investigated、WorldChanged、PlayerMoved 不会更新 PlayerKnowledge。抽屉操作还会需要 OperationCompleted，若使用 OperationDone 条件就不能省略。
- 从头重放可以替代周期快照，但必须有可重建的序号0初始状态和绑定案件修订；不能连初始化依据一起删掉。
- 尚未实现的 Condition 分支必须显式拒绝或验证为不可发布，不能当 true 或默默忽略。
- 隐藏内容、父链访问校验与持久化一致性需要在这一刀验证；复杂通路、主题审讯、推导来源等在下一条用到它们的切片前完成，不必统统列为第一行 Scala 的门槛。

**建议的开工门槛（本节为评审建议，尚未执行）：**

1. 选定一个最小、合法且引用完整的调查夹具，确定切片实际使用哪些字段／协议；不实现全部 153 个类型的行为。
2. 明确本切片入口访问、操作顺序、获知写入、原子提交与重放约定；修正其中已知冲突。
3. 对该夹具检查五个可观察结果：隐藏内容不能直接查询；打开后获得既定线索；重复请求不重复授予；旧序号不覆盖新状态；重新加载能恢复相同知识。
4. 满足前两项即可开始实现；第三项是切片完成标准。通过后停止这条路径的设计，进入下一项实际能力。

这比“所有 P0 做完才写第一行代码”更贴合增量开发，同时不把信息安全与数据一致性当成可无限延后的完整性优化。本轮未修改 case_truth_types.md，也未开始源码或 API 实现。
