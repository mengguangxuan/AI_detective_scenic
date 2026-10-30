# AI 侦探剧场：目录结构与典型代码讲解

适用版本：原讲解基于2026-10-29修订9；2026-10-30已统一人物作者资料并分离交互配置，新建修订10。警方运行资料现已并入WitnessProfile，police仅引用问询ID。人物结构以[人物资料与交互配置](../cases/character_configuration.md)为准，其余示例仍可用于课堂展示。

## 1. 如果只有五分钟，按这个顺序讲

| 时间 | 打开的文件 | 要讲的内容 |
|---|---|---|
| 约1分钟 | 本文第2节的目录树 | 前端、后端、案件配置和运行工具分别负责什么 |
| 约1分钟 | `cases/objects/ClueId.scala`、`WitnessFollowUpSpec.scala` | 用不同类型表达线索、人物、操作，避免编号混用 |
| 约1分钟 | `games/utils/FixtureRules.scala` | 输入旧状态，计算新状态，返回结果；计算过程不访问数据库 |
| 约1分钟 | `games/api/ExecuteOperationMessage.scala` | API编排身份检查、读取、规则计算和提交，SQL由Table封装 |
| 约1分钟 | `models/api/ChatCompletionClient.scala` | 模型接口独立，业务规则与具体供应商分开 |

可以这样开场：

> 这是一个Web侦探游戏，前端负责3D现场和调查交互，Scala后端负责案件规则、存档和人物问询。设计上主要体现三点：用强类型区分业务对象，用纯函数计算状态变化，用模块边界管理数据库和模型调用。

当前已实现现场调查、八个独立页面、人物／警方模型对话、地图、案件时钟、外部核查和补充供述。完整结案评分、复盘、动态案件生成和嫌疑人模式仍属后续计划，不能按规划文档中的目标宣称已经完成。

## 2. 项目的文件层级

```text
detective_scenic/
├── backend/                         Scala后端
│   ├── build.sbt                    编译配置、Scala版本和依赖
│   ├── config/                      模型配置模板及本地配置
│   └── src/
│       ├── main/scala/services/
│       │   ├── accounts/            本机演示身份、令牌解析
│       │   ├── cases/               固定案件内容、导入校验、人物资料
│       │   ├── games/               当前调查、操作规则、问询、存档
│       │   ├── models/              模型供应商接口、配置和HTTP调用
│       │   └── system/              消息协议、路由、数据库事务与启动
│       └── test/scala/services/     后端自动化测试
├── frontend/                        React + TypeScript前端
│   ├── package.json                 前端依赖和运行脚本
│   ├── vite.config.ts               开发／构建配置和API代理
│   ├── src/
│   │   ├── main.tsx                 React启动入口
│   │   ├── app/                     应用外壳、页面导航、案件时钟
│   │   ├── pages/                   各业务页面及页面专用组件
│   │   ├── features/investigation/  多个页面共用的调查状态与交互
│   │   ├── apis/                    按后端服务组织的API类和响应类型
│   │   ├── apiClient/               HTTP发送、响应校验和错误处理
│   │   ├── objects/                 前端共用的ID、序号等类型
│   │   └── styles.css               Tailwind入口和全局样式
│   └── tests/                       Playwright浏览器与重启恢复测试
├── docs/
│   ├── fixtures/                    作者维护的案件及人物对话配置
│   ├── cases/                       原著来源、案件核对和改编说明
│   ├── backend/                     后端契约与实现说明
│   ├── frontend/                    前端实现、启动验收与交互待办
│   ├── handoff/                     阶段进展和交接记录
│   └── classroom/                   本课堂讲解文档
├── tools/                           Windows／WSL启动与验证脚本
├── AGENTS.md                        项目约束、建模原则和阶段记录
├── README.md                        仓库说明
└── plan.md                          产品规划，包含尚未实现的目标
```

`target/`、`node_modules/`、`dist/`和`.build-cache/`是构建、依赖或本机测试产物，不是业务源码。课堂讲目录时可折叠。`backend/config/model-api.local.json`含本地密钥，不打开投屏。

### 2.1 后端按业务领域划分

| 模块 | 主要作用 | 适合打开的文件 |
|---|---|---|
| `accounts` | 创建本机身份，把令牌解析为可信用户；当前是本机演示身份机制 | `api/ResolveLocalIdentityMessage.scala` |
| `cases` | 管理固定案件修订、线索、人物、操作配置及引用校验 | `objects/CaseContent.scala`、`utils/FixtureLoader.scala` |
| `games` | 管理本局进度、已得线索、实际出示、问询、核查时钟及历史记录 | `api/ExecuteOperationMessage.scala`、`utils/FixtureRules.scala` |
| `models` | 定义对外模型接口，实现DeepSeek兼容HTTP调用 | `api/ChatCompletionClient.scala`、`utils/DeepSeekChatClient.scala` |
| `system` | 管理共享基础设施和程序启动 | `api/APIMessage.scala`、`api/DatabaseSession.scala`、`api/WebDemo.scala` |

这些是同一个后端进程内的业务模块，目前没有分别部署成五个网络微服务。

每个模块内部按需要使用以下目录，不强制建立空文件夹：

| 目录 | 放什么 | 边界 |
|---|---|---|
| `api/` | 独立的可执行消息，以及明确的公开能力接口 | 消息的`plan(connection)`显式组合副作用 |
| `objects/` | 领域数据、枚举、序列化；`apiTypes/`放API响应结构 | 不把数据库访问或有状态工作流塞进数据对象 |
| `routes/` | 消息注册、字段解析与可信身份接入 | 不直接信任浏览器提交的用户身份 |
| `tables/` | 每张表的初始化、公开读写方法、私有SQL和行映射 | 调用方使用表方法，不自行拼接SQL |
| `utils/` | 服务内部的规则计算和辅助逻辑 | 通过`private[games]`等可见性限制跨模块调用 |

场景与地图的七个类型现在放在`cases/objects/scene/`：`FixtureNode`、`FixturePoint`、`SceneObservation`、`SceneObjectBinding`、`AreaMap`、`MapLocation`、`MapRoad`。ID类型仍在`cases/objects/`根目录。这里保存固定场景配置，前端`pages/Scene/`负责实际3D渲染。目录分组没有改变类型数量或存档字段。

其余类型也按用途分组：`objects/content`保存事实、线索和公开人物简介；`objects/dialogue`保存人物资料与编译后的问询投影；`objects/investigation`保存人物交互、披露、阶段、对照和操作配置。根目录仅保留13个ID与CaseContent／CaseRevisionRecord，共15个文件；所有子目录合计仍42个类型。该目录调整没有改写材料语义或替玩家作推理判断。

例如`games/tables/SessionTable/`内有`SessionTable.scala`与`SessionTableInitializer.scala`，分别管理会话表访问和表初始化。

固定内容和本局状态也分开：`CaseContent`描述作者配置的案件内容；`GameSessionRecord`保存这次调查已完成的操作、已得线索和时间；`SessionRecord`保留行动与披露历史。更新本局进度不会改写原案件。

### 2.2 前端按页面划分

| 页面目录 | 地址 | 主要作用 |
|---|---|---|
| `pages/Case/` | `/case` | 案情简介与公开人物关系 |
| `pages/Map/` | `/map` | 地点连接、距离和路线估算 |
| `pages/Scene/` | `/scene` | 3D现场、镜头控制与物品操作菜单 |
| `pages/Clues/` | `/clues` | 已得线索、时间核对、供述版本对照 |
| `pages/People/` | `/people` | 人物资料及可用问询入口 |
| `pages/Interviews/` | `/interviews` | 人物对话、证据质询和补充问询 |
| `pages/Police/` | `/police` | 警方对话、调查安排与核查进展 |
| `pages/Journal/` | `/journal` | 调查记录检索与回看 |

页面入口`index.tsx`组织业务骨架；专用组件放在页面的`components/`，专用函数放在`functions/`。例如供述对照位于`pages/Clues/components/StatementComparisons.tsx`。

`features/investigation/`处理各页面共享的同一场调查：`hooks/useInvestigation.ts`协调请求和界面状态，`objects/`定义状态，`functions/`放状态计算，`utils/`处理浏览器存储和连接。

`apis/games/ExecuteOperationAPI.ts`表达具体请求；`apiClient/sendMessage.ts`负责统一发送和响应校验。3D画面通过节点和实体ID关联调查入口，实际线索与操作权限来自后端。

## 3. 用一次操作串起前后端

以点击抽屉操作或“开始补充问询”为例：

```text
页面按钮
  → useInvestigation协调交互状态
  → ExecuteOperationAPI构造请求
  → sendMessage发送JSON
  → 后端路由解析请求、解析身份
  → ExecuteOperationMessage.plan
      → Table锁定／读取会话、检查原请求回执
      → FixtureRules.execute计算合法状态变化
      → MutationSupport.commit持久化
  → 前端读取当前快照
  → 根据快照更新界面
```

请求携带`RequestId`和`expectedSequence`：前者用于识别同一次请求，后者用于检查玩家是否基于过期状态操作。提交后断线可以用原请求重试；数据库回执用于避免重复执行。这些是运行时一致性措施，不能仅靠类型检查完成。

## 4. 强类型：让错误尽量在编译阶段暴露

### 示例一：不同业务ID使用不同类型
源码：[ClueId.scala](../../backend/src/main/scala/services/cases/objects/ClueId.scala)。同目录还有`WitnessId`、`OperationId`、`RouteId`等。

```scala
package services.cases.objects

import io.circe.{Decoder, Encoder, KeyDecoder, KeyEncoder}

final case class ClueId(value: String)

object ClueId:
  def toString(value: ClueId): String = value.value
  def fromString(value: String): Either[String, ClueId] =
    Either.cond(value.nonEmpty && value == value.trim, ClueId(value), "Invalid ClueId")
  given Encoder[ClueId] = Encoder.encodeString.contramap(toString)
  given Decoder[ClueId] = Decoder.decodeString.emap(fromString)
  given KeyEncoder[ClueId] = KeyEncoder.encodeKeyString.contramap(toString)
  given KeyDecoder[ClueId] = KeyDecoder.instance(value => fromString(value).toOption)
```

可以这样讲：

> 线索ID和人物ID在JSON里都可以是字符串，但进入程序后具有不同类型。一个要求`ClueId`的参数不能直接传入`WitnessId`，编译器会指出混用。

下面是说明类型差异的示意，错误行不属于可编译的项目代码：

```scala
val clue: ClueId = ClueId("clue_clock")          // 正确
val wrong: ClueId = WitnessId("redding")        // 编译错误：类型不同
```

注意：`final case class`的构造器本身没有禁止所有非法字符串；JSON解码通过`fromString`检查非空和首尾空格。某个ID是否真的存在于当前案件，仍由案件引用校验确认。强类型不会自动证明证据内容真实。

### 示例二：业务配置明确关联类型
源码：[WitnessFollowUpSpec.scala](../../backend/src/main/scala/services/cases/objects/investigation/WitnessFollowUpSpec.scala)。

```scala
final case class WitnessFollowUpSpec(operation: OperationId, witness: WitnessId, title: String, requiredEvidence: Set[ClueId], requiredRoutes: Set[RouteId], knowledge: Vector[String], policy: Vector[String])
```

`requiredEvidence`只能放线索ID，`requiredRoutes`只能放路线ID，`witness`指向人物，`operation`指向开始操作。知识和对外策略采用语义文字，程序校验关联与执行条件。这样既保留案件描述的开放性，也让执行边界清楚。

### 示例三：有限状态使用枚举或可辨识联合类型
后端源码：[FixtureEffect.scala](../../backend/src/main/scala/services/cases/objects/investigation/FixtureEffect.scala)。

```scala
enum FixtureEffect:
  case Open(entity: EntityId)
  case SetOpened(entity: EntityId, opened: Boolean)
  case SetNodeVisible(node: SceneNodeId, visible: Boolean)
```

这是执行器明确支持的有限操作集合。`FixtureRules`用`match`处理每一种效果，新增分支时可利用编译器的匹配检查发现遗漏。犯罪动机、人物关系等开放内容不靠枚举穷举。

前端也用类型约束页面状态：
源码：[InvestigationPageState.ts](../../frontend/src/features/investigation/objects/InvestigationPageState.ts)。

```typescript
export type InvestigationPageState =
  | { kind: 'loading' }
  | { kind: 'failed'; message: string }
  | { kind: 'ready'; snapshot: InvestigationSnapshot; selected: SceneSelection; notice: string;
      mutation: { kind: 'idle' } | { kind: 'submitting' } | { kind: 'outcomeUnknown'; pending: PendingCommand; problem: string }
        | { kind: 'awaitingSnapshot'; problem: string } };
```

只有`kind === 'ready'`的分支才能直接使用`snapshot`。提交中、结果未知、已提交但等待快照也分别表达，减少多个布尔变量形成矛盾组合。前端ID还使用Zod品牌类型，例如`src/objects/ClueId.ts`；品牌主要提供编译期区分，运行时仍需schema解析。

## 5. 纯函数：先计算，再持久化

### 示例四：规则返回ActionPlan，不直接写数据库
源码：[FixtureRules.scala](../../backend/src/main/scala/services/games/utils/FixtureRules.scala)的`execute`，也可以对照同文件的`investigate`。

```scala
  private[games] def execute(f: CaseContent,state: GameSessionRecord,request: RequestId,operation: OperationId, records: Vector[SessionRecord] = Vector.empty, turns: Vector[InterviewTurnRecord] = Vector.empty): Either[GameError,ActionPlan] =
    for
      op <- f.operations.find(_.id==operation).toRight(denied)
      _ <- f.points.find(_.id==op.point).filter(accessible(f,state,_)).toRight(denied)
      _ <- Either.cond(operationAvailable(state,op) && FollowUpRules.permitted(f,state,operation,records,turns),(),GameError(ErrorCode.ConditionFailed,"当前状态不允许执行该操作。"))
    yield
      val changed=op.effects.foldLeft(state) { (s,effect) => effect match
        case FixtureEffect.Open(entity) => s.copy(opened=s.opened+entity)
        case FixtureEffect.SetOpened(entity, opened) => s.copy(opened=if opened then s.opened+entity else s.opened-entity)
        case FixtureEffect.SetNodeVisible(node,true) => s.copy(visibleNodes=s.visibleNodes+node)
        case FixtureEffect.SetNodeVisible(node,false) => s.copy(visibleNodes=s.visibleNodes-node)
      }
      val next=changed.copy(sequence=Sequence(state.sequence.value+1),completedOperations=changed.completedOperations+operation)
      val record=SessionRecord(RecordId(s"${request.value}:operation"),state.id,request,next.sequence,next.storyTimeSeconds,
        op.point,None,Some(operation),op.label,Set.empty,Set.empty)
      ActionPlan(next,record,CommandResult(next.sequence,Vector.empty,Set.empty))
```

讲解时依次指出：

1. 案件内容、旧状态、请求ID和历史材料全部通过参数传入。
2. `Either[GameError, ActionPlan]`明确表示失败或成功；前提不满足返回`Left`。
3. `foldLeft`处理有限效果，`copy`产生新状态，旧状态保持不变。
4. `ActionPlan`同时携带候选状态、调查记录和返回结果，交给外层统一提交。
5. 计算过程中没有数据库连接、网络调用、系统时间读取或随机ID生成。

可以这样讲：

> 这个函数负责算出操作应该产生什么结果。相同输入会得到相同的计算结果，所以可以独立测试，也能在失败时保持旧状态。结果真正生效还需要外层事务成功提交。

`ActionPlan.session`是规则产生的候选状态，提交层还会结算案件时间和到期核查；不能把它直接说成数据库已经保存的最终状态。

### 示例五：前端也把状态计算与副作用分开
源码：[acceptSnapshot.ts](../../frontend/src/features/investigation/functions/acceptSnapshot.ts)。

```typescript
export function acceptSnapshot(previous: InvestigationSnapshot | null, next: InvestigationSnapshot): InvestigationSnapshot {
  if (previous && (previous.sessionId !== next.sessionId || previous.caseId !== next.caseId || previous.caseRevision !== next.caseRevision))
    throw new Error('服务器返回了不同会话或案件修订。');
  return previous && previous.sequence > next.sequence ? previous : next;
}
```

这个函数比较前后两份快照：拒绝不同会话或案件修订，避免旧序号响应覆盖较新状态。它只读取参数，不发送请求、不写存储、不调用React的`setState`。错误通过抛出异常表达，因此它是确定性的、可能失败的计算，并非对所有输入都返回正常值的全函数。

配套测试：`frontend/src/features/investigation/functions/acceptSnapshot.test.ts`，可以展示“较旧响应不回退界面”的用例。

## 6. 模块化：把规则、持久化和外部模型分开

### 示例六：API消息展示完整业务步骤，Table封装SQL
源码：[ExecuteOperationMessage.scala](../../backend/src/main/scala/services/games/api/ExecuteOperationMessage.scala)。

```scala
final case class ExecuteOperationMessage(userId: UserId,sessionId: SessionId,requestId: RequestId,expectedSequence: Sequence,operation: OperationId) extends APIMessage[CommandResult]:
  override def plan(connection: Connection): IO[CommandResult] =
    val digest=MutationSupport.operationDigest(userId,sessionId,requestId,expectedSequence,operation)
    for
      _ <- IO.blocking(require(!connection.getAutoCommit,"Message requires transaction"))
      session <- SessionTable.lock(connection,sessionId).flatMap(MutationSupport.owned(_,userId))
      receipt <- ReceiptTable.find(connection,sessionId,requestId)
      result <- receipt match
        case Some(saved) => MutationSupport.retry(saved,digest)
        case None =>
          for
            _ <- MutationSupport.checkSequence(session,expectedSequence)
            revision <- CaseRevisionTable.find(connection,session.caseId,session.caseRevision).flatMap(value => IO.fromOption(value)(GameError(ErrorCode.InvalidValue,"案件修订不存在。")))
            records <- SessionRecordTable.list(connection,sessionId)
            turns <- InterviewTurnTable.list(connection,sessionId)
            plan <- IO.fromEither(FixtureRules.execute(revision.content,session,requestId,operation,records,turns))
            result <- MutationSupport.commit(connection,session,requestId,digest,plan)
          yield result
    yield result
```

课堂上重点指向这四步：

| 代码位置 | 职责 |
|---|---|
| `SessionTable.lock`、`MutationSupport.owned` | 读取并锁定会话，检查访问者身份 |
| 回执与`checkSequence` | 处理同一请求重试，检查状态版本 |
| `FixtureRules.execute` | 调用纯规则，计算ActionPlan |
| `MutationSupport.commit` | 持久化状态、记录与成功回执，并结算时间 |

公开消息遵循统一契约：
```scala
trait APIMessage[Response]:
  def plan(connection: Connection): IO[Response]
```

`IO[Response]`表示需要执行的效果计算。`for`表达式组织数据库调用、检查和提交顺序；使用了`IO`不代表这些操作变成了纯业务计算。事务由`system/api/DatabaseSession.scala`在调用边界管理，消息要求在事务中运行。

可以接着打开以下文件：

- `games/tables/SessionTable/SessionTable.scala`：SQL为`private val`，公开`find`、`lock`、`update`等类型明确的`IO`方法；内部负责参数绑定、执行和行映射。
- `games/utils/MutationSupport.scala`：集中提交候选状态、日志和回执，避免各消息遗漏某一项。
- `system/api/DatabaseSession.scala`：统一管理连接资源与提交、回滚、取消清理。

模块间通过公开API或封装好的公开Table方法协作。`private[games] object FixtureRules`限制内部规则对象的访问范围，其他业务模块不能随意依赖其私有实现。

### 示例七：独立模型接口，可以替换供应商与测试实现
源码：[ChatCompletionClient.scala](../../backend/src/main/scala/services/models/api/ChatCompletionClient.scala)。

```scala
trait ChatCompletionClient:
  def complete(input: ChatCompletionInput): IO[String]
```

| 部分 | 职责 |
|---|---|
| `ChatCompletionClient` | 定义“给定输入，异步获得模型输出”的能力契约 |
| `DeepSeekChatClient` | 处理具体HTTP请求、认证和供应商响应 |
| `ModelSettingsLoader` | 读取、校验模型配置 |
| `games/api/AskWitnessMessage.scala` | 准备本局合法上下文，协调请求与模型调用 |
| `games/api/FinishInterviewMessage.scala` | 完成回复校验及本局状态提交 |
| `games/utils/InterviewRules.scala` | 组织角色知识、校验披露并计算允许的结果 |

模型接口没有JDBC连接参数，因为对外HTTP调用不属于数据库访问。问询采用短事务准备、事务外调用模型、短事务完成的流程，避免等待模型时一直占用会话锁；若调查已经变化，完成时还要检查状态。

`ChatCompletionClient`是对外能力接口，不是浏览器直接调用的API消息，所以没有强行给它添加`plan(connection)`。测试可以实现这个trait，稳定返回指定结果，无需真实密钥。实际例子见`backend/src/test/scala/services/games/FollowUpSuite.scala`中的`client`辅助方法。

可以这样讲：

> 游戏逻辑依赖模型接口，具体供应商藏在实现后面。模型负责生成自然语言并申报披露的材料ID，后端还要检查这些ID和前提，才能修改游戏状态。这样更换供应商或接入测试替身时，调查规则可以继续复用。

ID和权限校验不保证自然语言正文绝对正确，所以项目同时保留作者配置的确定性问答和真实模型抽查。

## 7. 可以展示哪些测试

| 测试文件 | 展示的业务保证 |
|---|---|
| `backend/src/test/scala/services/games/FollowUpSuite.scala` | 只有线索不等于向本人出示；开始操作不自动生成新供述；重试和恢复保留首供 |
| `backend/src/test/scala/services/cases/FollowUpConfigSuite.scala` | 拒绝人物归属错误、无法到达的阶段依赖与错误对照引用 |
| `frontend/src/features/investigation/functions/acceptSnapshot.test.ts` | 旧响应不能覆盖新状态 |
| `frontend/tests/followups.spec.ts` | 从浏览器操作到后端存档，再到供述对照的完整流程 |
| `frontend/tests/restart.check.ts` | 实际重启后端后恢复已保存调查 |

优先在编辑器中打开一两个测试说明断言；临近上课无需为了展示代码重跑全部测试。最近一次已完成的验证为后端84项、前端单元12项、浏览器32项和重启恢复6项，通过情况记录在前端实现文档；这些是已有验证记录，不代表本文生成时重新执行了测试。

## 8. 老师可能追问的问题

**为什么很多对象只有字段和编解码器？**

数据对象负责表达结构；操作规则放在服务内部，副作用在API和Table层组合。每个有意义的类型独立文件，也符合课程要求，便于定位。

**既然强类型了，为什么还需要校验JSON？**

JSON是外部输入，编译器管不到网络传来的值。解码先检查数据形状和基本取值，案件加载器再检查引用和依赖，操作时还要检查身份及当前会话前提。

**为什么不让AI直接修改线索和状态？**

自然语言输出不稳定。后端只接收经过检查的材料引用和受支持操作，按固定案件与本局条件计算状态变化。模型表达与规则执行分开。

**为什么不把所有东西都写成纯函数？**

数据库、网络和浏览器存储必须产生副作用。项目把可计算规则尽量写成纯函数，再用明确的`IO`或前端请求／存储边界执行副作用。

**当前结构还有什么可改进？**

案件加载校验和快照投影较集中，部分配置引用需要多处手工维护；后续可局部拆分校验并改善错误定位。当前已经有类型和模块边界，但不宣称架构已经没有维护成本。

## 9. 上课前快速准备

1. 打开本文件及第1节列出的五组源码，提前折叠长导入、编解码器和SQL细节。
2. 先讲目录，再用一次操作贯穿前后端，最后逐个展示类型、纯计算、效果边界。
3. 需要现场演示时，按[验收前准备与启动步骤](../frontend/acceptance_preparation.md)启动；新版流程见该文第16节。
4. 不投屏本地密钥、完整私人角色资料或数据库文件。讲模型接入时展示接口和配置模板即可。


可以把对象的结构再拆分，按照更小的结构（比如case，person）等
前后端的类型应该对齐，API也该对齐。
