# API、消息与传输类型草案

本文从 `case_truth_types.md` 分离已有的接口建议，供后续讨论；不是定稿，不代表已经创建消息类、路由或源码。当前优先讨论[领域类型](case_truth_types.md)，暂不扩充 API。

领域类型描述案件、人物认知和会话变化；本文描述操作入口、消息、玩家响应及课程实现边界。原文第 23、24 节内容迁移至此，保留原有建议与待解决问题，不因迁移而确认接口数量、模块划分或实现方案。课程规范见 `../../../samples/samples/rules.txt`。

## 1. 请求与命令草案

以下是原有输入草案，尚需在 API 设计阶段决定领域命令与传输消息如何对应；不强制领域模型依赖这些请求包装。RequestId、SessionId、Sequence 仍在领域文档定义，因为日志、回执和并发状态也使用它们。

```scala
enum InvestigationInput:
  case Fixed(route: RouteId)
  case Free(text: Text)
enum PlayerCommand:
  case Investigate(point: PointId, input: InvestigationInput)
  case Execute(operation: OperationId)
  case Speak(person: PersonId, text: Text, presentedClues: Set[ClueId])
  case ReadReport(report: ReportId, route: RouteId)
  case Move(place: PlaceId, position: Vec3)
  case Submit(answer: Accusation)
final case class GameRequest(
  id: RequestId, session: SessionId, expectedSequence: Sequence,
  command: PlayerCommand
)
```

可信身份由路由验证，不接受客户端自报的 actor；请求不能携带任意状态补丁或可执行效果。领域错误 ErrorCode、ValidationIssue、GameError 见领域文档第 18 节。

## 2. 玩家响应与安全指控选项草案

```scala
final case class FixedRouteView(id: RouteId, label: Text)
final case class OperationChoiceView(id: OperationId, label: Text)
final case class SceneNodeView(id: SceneNodeId, asset: AssetId, localToPlace: Matrix4)
final case class InquiryPointView(
  id: PointId, label: Text, position: Vec3, radiusMeters: Double,
  fixedRoutes: Vector[FixedRouteView], operations: Vector[OperationChoiceView],
  freeInvestigationAllowed: Boolean
)
final case class ConnectionView(to: PlaceId, label: Text, open: Boolean, arrival: Vec3)
final case class PlayerSceneView(
  place: PlaceId, placeName: Text, sequence: Sequence,
  nodes: Vector[SceneNodeView], assets: Vector[Asset],
  points: Vector[InquiryPointView], exits: Vector[ConnectionView],
  playerPosition: Vec3
)
final case class ClueView(
  id: ClueId, title: Text, result: FindingContent, suggestedPeople: Set[PersonId]
)
final case class OptionView(id: OptionId, label: Text)
final case class QuestionView(
  id: QuestionId, label: Text, minimumSelections: Int, maximumSelections: Int,
  options: Vector[OptionView]
)
final case class AccusationOptionsView(incident: IncidentId, questions: Vector[QuestionView])
final case class QuestionFeedbackView(question: QuestionId, label: Text, passed: Boolean)
final case class VerdictView(
  score: Int, maximumScore: Int, passed: Boolean, feedback: Vector[QuestionFeedbackView]
)
final case class CaseSummary(
  id: CaseId, revision: Revision, title: Text, introduction: Text,
  estimatedMinutes: Int, cover: Option[AssetId]
)
```

PlayerSceneView 仅含当前可见节点与资产，矩阵已展开，不暴露内部层级、represents、Topic、Fact、SceneCue、条件树和真相。nodes 可为空，不强迫每个材料场景有模型。此 DTO 的 points 只对应当前可见 World／OnNode 入口；报告／对话／日志有各自的界面投影，不给它们伪造世界坐标。当前状态序号随响应返回，用于后续乐观锁。

**AccusationOptionsView 绝不包含 SolutionRequirement、correctOptions、acceptedClueSets、Fact.explanation 或 isCorrect。** OptionId 不绑定“只有正确说法才算 Fact”的假设；错误候选是正常待选假设。问题可允许多选，支持共同作案。完整 Verdict 留后端，结案前仅返回玩家本次提交相关反馈；完整真相复盘须经过会话权限与结束状态检查。

CaseSummary 是列表元数据，不依赖强制 Difficulty 枚举、创建者头像或生成能力。estimatedMinutes > 0，cover 仅引用允许公开的资源；具体玩法裁剪与页面调整另行决定，不为凑页面新增技术监控页。

## 3. 课程实现边界备忘

### 3.1 归属与依赖

| 所属服务 | 维护的数据／规则 |
|---|---|
| system | 通用 Text／时间／空间／版本值类型、运行标识 UserId／SessionId／RequestId／RecordId／AiCallId、NonEmptyVector、共享错误和序列化基础设施 |
| accounts | UserProfile、账户表与身份校验消息 |
| cases | 案件标识与全部固定世界／真相／初始视角／报告／场景／调查规则／选项／答案；Observer、InformationSource、LearnedInformation、预设未来观察和初始配置也属于固定契约；validateCase、deriveFacts 等纯内容规则 |
| games | 当前状态、全部本局 Record／日志／快照／请求回执、AI 运行上下文、指控请求和结果、玩家投影及会话 Table；运行时校验、受理、提交与纯重放 |

依赖方向为 accounts→system、cases→system、games→cases／accounts／system；cases 不导入 games 的对象或 utils。固定视角中的本局记录 ID 只引用 system 标识，不造成 cases→games 类型依赖。cases 的初始配置不直接包含 NpcState，games 创建会话时才组装当前状态。objects 可按 truth、perspective、scene、investigation、session 等清晰子域分类，不要求百余文件平铺。

`deriveFacts` 维护在 cases/utils，private[cases]。cases 发布校验直接组合本服务纯函数；games 通过 cases 的公开内部 `DeriveFactsMessage(...).plan(connection)` 得到已批准结论，再记录 FactsEstablished。重放只应用日志中的批准结论，不为了恢复状态再调用 API。不要要求一个纯函数内部调用返回 IO 的消息。

读取案件可直接调用 cases 的公开 `CaseRevisionTable.findPublished(connection,id,revision)`；已有该封装时无需再创建仅转发的 LoadCasePackageMessage。内部 DeriveFactsMessage 不注册到前端路由；其完整案件和推导结果都不是玩家响应。

### 3.2 API 边界

PlayerCommand／GameRequest 是领域命令／去重输入，不替代独立 API 消息。实际源码每个公开操作独立 `final case class ...Message`，业务字段放构造参数，继承 APIMessage[Response] 或确实需要令牌转换的 APIWithTokenMessage[Response]，以 `plan(connection): IO[Response]` 执行。变更消息必须携带 RequestId、SessionId、expectedSequence，不能像评审示例那样遗漏去重编号。

| 操作 | 建议消息 | 对外返回主体 |
|---|---|---|
| 案件列表／简介 | ListCasesMessage、GetCaseBriefingMessage | CaseSummary、PublicBackground 的安全响应包装 |
| 开始／恢复 | StartGameMessage、GetGameStateMessage | 会话编号、当前序号和玩家状态投影 |
| 场景 | GetSceneViewMessage | PlayerSceneView |
| 检查／操作／移动 | InvestigateMessage、ExecuteOperationMessage、MoveMessage | 新序号、该次公开检查内容及必要界面变化 |
| 审讯／读取报告 | InterrogateMessage、ReadReportMessage | 经批准的对话／报告投影及新序号 |
| 指控候选／提交 | GetAccusationOptionsMessage、SubmitAccusationMessage | AccusationOptionsView／VerdictView |
| 复盘 | GetCaseReviewMessage | 仅在允许结束状态下展示的复盘响应 |

上表是落地清单，不宣称已创建消息类。每个 API 有一个 apiTypes 响应包装；不能直接返回 AccusationRecord、GameState、CasePackage 或 SolutionRequirement。账户操作沿用课程架构，NoRequestMessage 只用于无业务参数操作；需要可信 userId 的 GetCurrentUser 不是为了省字段而定义空请求。

课程规则的推荐统一命名与当前样本实现有细节差异：样本去除 `APIMessage` 后追加 `API` 再转小写，并非简单移除 `Message`。项目应选择一套统一规则并同时实现前后端名称计算，不能直接把 `SubmitAccusationMessage` 丢进旧工具后假定路由正确。各服务 XXRoutes 注册公开消息，顶层只合并列表；内部消息不注册。plan.md 的 REST 路径是参考，不锁定为最终接口。

样本 APIWithTokenMessage 配合路由先校验 userToken、覆盖可信 userId，再构造消息；单有 marker trait 不足以代表已鉴权。HttpError 当前样本仅有 BadRequest、Unauthorized、Forbidden、NotFound、Conflict，不存在提案所写的 Unprocessable 或 unavailable；若需要 503 等类型，必须同时扩展异常与路由映射，不能调用未定义工厂。

错误映射可留在 system 的共享传输边界，但它是基础函数，不是名为 GameErrorMapping 的伪业务消息。游戏不可访问与对象不存在的公开信息策略须统一，不能借 NotFound 泄露隐藏物品。AI 原始错误、内部路径和真相不能直接放入 publicMessage。

**模型调用事务另行明确：** 样本路由默认整段 plan 包在事务中，长时间 LLM 请求不能因此一直持有行锁／数据库连接。实现前应补路由基础设施支持“短事务登记请求与读快照 → 释放连接调用模型 → 新短事务复核序号并提交”。仍保留 plan(connection) 作为消息入口，不偷偷用悬空连接执行后续查询，也不把网络等待放入事务。这是本版识别出的实现前置设计项，尚未生成基础设施源码。

### 3.3 序列化与存储真值

- ID、Text 的解码器验证非空与约定格式；Map 键类型须提供 Circe KeyEncoder／KeyDecoder。验证仅写在 validateCase 中不足以保护 API 输入；直接构造值也须经过相同校验边界。
- KeyDecoder 的验证型实现可用 `KeyDecoder.instance(raw => fromString(raw).toOption)`；评审中的 `KeyDecoder.decodeKeyString.emap(...)` 不能未经版本核对当作可编译模板。不可默默 trim 并归一化不同 ID 而产生键碰撞。
- NonEmptyVector 可采用项目自有 head+tail 结构及数组编解码；也可复用已有经过验证的集合，cats-effect 已带来 Cats 生态，不能以“消除 cats.data 依赖”作为强制重造集合的理由。
- **整个 ADT 分类，不逐分支分类。** 纯无载荷枚举采用伴生对象 toString/fromString；包含载荷的整个枚举统一 tagged object JSON，包含无参数分支时也保持相同形态。DiscoveryMethod 的 Fixed(label) 也是有载荷；Observer 也不能按 Player／Npc 两种编码风格混用。
- 判别字段统一 `kind`，明确每个 case 的字段。普通 deriveEncoder／deriveDecoder 不自动保证这种形状；选定 Circe 版本后使用明确配置或显式伴生编解码器，并以往返测试确认，不复制一个未经验证的注解名。
- SessionCommit 可存为经过类型化解码的 jsonb，jsonb 不等于任意字典。已提交日志包含自足的 Record 内容，Record 表是可重建索引投影；不采纳“日志只存引用、另表记录可独立修改”以免破坏重放。若改为引用，记录表必须不可变、版本固定并同事务写入，另行调整契约。
- 新增可选字段、变更枚举或候选结构不天然前向兼容；明确 schemaVersion／reducerVersion，旧日志使用旧解码器或经过验证的迁移。文档 v1.3 不是运行时版本自动迁移标记。

## 4. 最小切片的内部消息实现

已有 LoadFixtureMessage、StartGameMessage、ExecuteOperationMessage、InvestigateMessage、GetGameStateMessage，均是本地 Scala 切片的内部调用，未注册 HTTP 路由，不替代本文未来公开接口方案。StartGame 接受经加载器验证的 InvestigationFixture，执行消息接收可信 UserId、SessionId、RequestId、expectedSequence；具体构造与响应见源码及 [minimal_backend.md](minimal_backend.md)。

这里只落实最小调查路径；不据此扩充其他 API。前端令牌转换、服务路由注册和 AI 短事务仍需在实际使用前设计。

## 5. 前端调查视图的待议补充

[前端类型初稿](../frontend/frontend_types.md) 提议：同一序号的场景／已获线索／公开日志组成一致快照，场景节点增加可登记的 visualPresetId，变更操作先取得回执再刷新快照。首版仅固定操作与检查。此提议不修改现有 Scala 响应或路由，也不自动确认通用姿态绑定的领域实现；相关后端投影在浏览器切片前局部落实。


> 2026-10-27 实施同步：最小 Scala 切片已按四张表重构，具体实现及验收命令以 [minimal_backend.md](minimal_backend.md) 为准。当前状态直接存储；旧提交日志重放仅用于离线迁移。导入入口为 ImportCaseMessage，操作和调查直接返回 CommandResult，不再增加 result 包装。本文件中尚未实施的完整领域或接口仍是设计草案。


## 已落地的本机 HTTP 切片

以下为已注册的独立消息，不代表本文所有接口已实现。HTTP 统一 POST /api/{消息名去掉Message后转小写}；前端对应 API 类按相同规则去掉API后缀。JSON 直接返回 DTO，无 result 包装。服务器在边界读取并验证本机 Cookie，替换为可信 UserId，拒绝输入中伪造的 userId。

| 消息 | 外部 JSON 字段 | 响应 |
|---|---|---|
| OpenLocalIdentityMessage | 无业务字段，发送 {}；已有凭证来自 Cookie | mode=local-demo，并设置 HttpOnly Cookie |
| OpenStudyMessage | sessionId（浏览器先保存的 UUID） | InvestigationSnapshot；固定公开书房，创建重试不重置进度 |
| GetInvestigationSnapshotMessage | sessionId | InvestigationSnapshot；只含获准内容 |
| ExecuteOperationMessage | sessionId, requestId, expectedSequence, operation | CommandResult |
| InvestigateMessage | sessionId, requestId, expectedSequence, point, route | CommandResult |

Sequence 仍为 {value: 整数}，HTTP 限制为 JS 安全非负整数。401表示演示凭证失效，403表示会话／入口不可访问，409表示过期序号、载荷冲突或条件不满足；格式错误400，内部失败500且不回传堆栈。网络错误、无法解码成功响应和500都按结果不确定处理，保留原请求；已成功但读取失败只刷新视图。

GetInvestigationSnapshotMessage 锁定会话后读取绑定案件修订和本会话记录，投影固定 study/1 的表现、获准操作、已获线索和公开日志。该受限视图未采用本文尚待实现的通用矩阵协议。完整启动与恢复验收见 ../frontend/implementation.md。
