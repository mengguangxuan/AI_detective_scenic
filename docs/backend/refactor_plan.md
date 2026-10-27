# 最小切片重构计划（对标 `D:\2026autumn\software_making\samples\samples\sample\backend-sample`）

> **本文性质**：可执行的改动清单。不是评审意见，是照着做就能改完的施工图。
>
> **依据优先级**：`rules.txt`（结构硬要求）> `backend-sample`（规模与粒度基线） > 本文建议。
> 规模、类型粒度、分层数量跟老师示例；表归属、Message 形态、可见性等结构要求跟 `rules.txt`。
> 两者冲突处见第 6 节。
>
> **目标**：把领域概念从 16 压到 12，表从 3 补到 3（但补上缺失的那一类），让 `cases` / `games` 两个服务与老师的 `books` / `user` 同构。
>
> 关联文档：[minimal_backend.md](minimal_backend.md)、[minimal_fixture.md](minimal_fixture.md)、[case_truth_types.md](case_truth_types.md)、[../frontend/frontend_types.md](../frontend/frontend_types.md)

---

## 0. 目标状态一览

| | 现在 | 改完 | 老师 books | 老师 user |
|---|---|---|---|---|
| `cases` 领域概念 | 8 | 8 | 7 | — |
| `games` 领域概念 | 8 | **4** | — | 4 |
| **合计** | **16** | **12** | — | — |
| `cases` 表 | **0** | **1** | 2 | — |
| `games` 表 | 3 | **2** | — | 2 |
| DTO | 5 | **3** | 4 | 2 |
| Message | 5 | 5 | 9 | 5 |

**判定标准**：改完之后 `games` 服务的形状应当与老师的 `user` 服务几乎一致——1 个贯穿行与状态的类型 + 1 个追加记录类型 + 2 个 DTO + 2 张表。

### 核心取舍

> **删掉"没有消费者"的机制，保留"有真实消费者"的机制。**

| 机制 | 消费者 | 结论 |
|---|---|---|
| 事件溯源 / replay / reducerVersion | `plan.md` 没有任何页面需要按序号回放 | **删** |
| 幂等 + 请求回执 | 前端 `outcomeUnknown` 协议、网络重试 | **留** |
| 案件版本冻结 | 会话绑定 revision、旧存档恢复 | **留** |
| `FixtureLoader` 整图校验 | 案件引用完整性 | **留** |
| `FixtureRules` 访问判定 | 隐藏父链、位置半径 | **留** |

---

## 1. 基线：老师示例的精确数字

| | Message | 对象文件 | **领域类型** | DTO | 表 |
|---|---|---|---|---|---|
| `books` | 9 | 8 | **7** | 4 | 2 |
| `user` | 5 | 5 | **4** | 2 | 2 |

`books` 的 7 个类型 = `BookId`、`BorrowRecordId`（ID）+ `BookInventoryStatus`、`BorrowRecordStatus`（状态枚举）+ `BookRecord`、`BorrowRecord`（实体记录）+ `BookSaveData`（输入收拢）。

### 可直接迁移的五条

1. **一个类型贯穿行 / 状态 / 返回**（`BookRecord` 只有一个）。
2. **状态就地 `UPDATE`**（`BookTable.decrementAvailable` 返回 `IO[Boolean]`），不做事件累积。
3. **追加型数据用追加表**（`borrow_records`），不塞进主行。
4. **输入收拢类型只做校验，不持久化、不带 codec**（`BookSaveData`）。
5. **建表与灌数据分成两个方法**（`initialize` / `seedSamples`）。

### 不能迁移的四条

| # | 老师没有 | 我们的原因 |
|---|---|---|
| 1 | 案件版本冻结 | 老师可以 `UpdateBook` 直接改行；我们一局游戏绑定 `revision`，发布后不得原地修改 → 必须 `PRIMARY KEY (case_id, revision)` 且只插不改 |
| 2 | 跨行引用完整性 | 一本书是一行独立数据；我们的案件是 `node → point → route → clue → fact` 的图，错一个 ID 整局崩 → `FixtureLoader` 的 171 行不能省 |
| 3 | 累积集合状态 | 库存是整数比较；我们是五个集合 + 先判访问权再判可改性 → `FixtureRules` 的 80 行不能省 |
| 4 | 幂等与回执 | 老师的 `BorrowBookAPIMessage` 没有 `requestId`，网络重试会重复扣库存。我们保留 `game_receipts` |

第 4 条是本计划唯一的"逆老师"决定。理由：前端 `outcomeUnknown → 同 RequestId 重试` 协议建在它上面，删掉会使前端那份文档整节作废。成本是 1 张表 + 1 个类型 + 约 30 行。

---

## 2. 批一：零风险纯删（约 30 分钟）

改动全部是删除，编译错误驱动，无数据影响。

### 2.1 删死代码类型

- 删 `backend/src/main/scala/services/cases/objects/TopicId.scala`
  - 已核实：10 处引用全在该文件自身（定义 + `toString`/`fromString` + 4 个 `given`），别处 **0 引用**。

### 2.2 删两个同形响应

- 删 `services/games/objects/apiTypes/InvestigateResponse.scala`
- 删 `services/games/objects/apiTypes/ExecuteOperationResponse.scala`
- `InvestigateMessage extends APIMessage[CommandResult]`
- `ExecuteOperationMessage extends APIMessage[CommandResult]`

两者的返回体都是 `wrap(CommandResult(result))`，形状完全相同。规则 24 要求"每个 API 一个返回结果"，不要求返回类型各不相同。

### 2.3 删 `FixtureCommand`

- 删 `services/games/objects/FixtureCommand.scala`
- `MutationSupport.digest` 改为直接对字段拼接：

```scala
private[games] def digest(user: UserId, session: SessionId, request: RequestId,
    expected: Sequence, action: String, target: String): String =
  val payload = s"${user.value}|${session.value}|${request.value}|${expected.value}|$action|$target"
  MessageDigest.getInstance("SHA-256")
    .digest(payload.getBytes(StandardCharsets.UTF_8))
    .map(b => f"${b & 0xff}%02x").mkString
```

调用方：`InvestigateMessage` 用 `("Investigate", s"${point.value}:${route.value}")`；`ExecuteOperationMessage` 用 `("Execute", operation.value)`。

### 2.4 验收

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 test
```

`Failed 0, Errors 0` 即通过。

> **注意**：摘要算法变了，**已存回执的 digest 会全部不匹配**，旧会话重试会报 `RequestConflict`。会在批二中重建数据库，可以接受。若不想现在破，把这一小项挪到批二末尾。

---

## 3. 批二：`cases` 建表（约 1.5 小时）

这一批解决三件事：补上缺失的表、让案件有唯一权威位置、解掉"加字段破数据"的死锁。

### 3.1 新表 DDL

`backend/src/main/scala/services/cases/tables/caserevision/CaseRevisionTableInitializer.scala`：

```scala
private val createSql = """CREATE TABLE IF NOT EXISTS case_revisions (
  case_id        VARCHAR(200) NOT NULL,
  revision       INT NOT NULL CHECK (revision > 0),
  schema_version INT NOT NULL,
  body           CLOB NOT NULL,
  PRIMARY KEY (case_id, revision)
)"""
```

`PRIMARY KEY (case_id, revision)` 就是"案件修订不可原地修改"这条领域规则的物理实现——想改只能插新 revision。

`body` 存 `InvestigationFixture` 的 JSON。这里允许整块存，因为案件从不按内部字段查询（判据见第 7 节）。

### 3.2 表方法（照 `BookTable` 的写法：SQL 私有、与方法相邻、行映射私有）

`services/cases/tables/caserevision/CaseRevisionTable.scala`：

```scala
object CaseRevisionTable:
  private val insertSql = "INSERT INTO case_revisions(case_id, revision, schema_version, body) VALUES (?, ?, ?, ?)"
  def insert(connection: Connection, caseId: CaseId, revision: Revision,
             schemaVersion: Int, body: String): IO[Unit] = ???

  private val findSql = "SELECT schema_version, body FROM case_revisions WHERE case_id = ? AND revision = ?"
  def find(connection: Connection, caseId: CaseId, revision: Revision): IO[Option[(Int, String)]] = ???

  private val listSql = "SELECT revision FROM case_revisions WHERE case_id = ? ORDER BY revision"
  def listRevisions(connection: Connection, caseId: CaseId): IO[Vector[Revision]] = ???
```

**可见性**：这三个方法必须是 `public`（不是 `private[cases]`），因为 `games` 要跨服务读——这正是规则 6/10 与老师示例冲突的地方（见第 6 节）。

### 3.3 导入消息（替代 `LoadFixtureMessage`）

`services/cases/api/ImportCaseMessage.scala`：

```scala
// 制作期内部入口，读文件后校验并写入案件表；不注册到前端路由。
final case class ImportCaseMessage(path: Path) extends APIMessage[CaseId]:
  override def plan(connection: Connection): IO[CaseId] =
    for
      raw <- IO.blocking(Files.readString(path))
      fixture <- IO.fromEither(FixtureLoader.decode(raw))
      _ <- CaseRevisionTable.insert(connection, fixture.caseId, fixture.revision, 2, raw)
    yield fixture.caseId
```

要点：**先把原始 JSON 校验完再入库**，保证表里只有能解出来的数据。入库的是原文字符串，不是再序列化一次（避免二次编码差异）。

### 3.4 `StoredSession` 去掉内嵌 fixture

```scala
// 改前
final case class StoredSession(id, owner, fixture: InvestigationFixture, initialState: FixtureState, sequence)

// 改后
final case class StoredSession(id: SessionId, owner: UserId,
  caseId: CaseId, caseRevision: Revision, initialState: FixtureState, sequence: Sequence)
```

`StartGameMessage` 改为：

```scala
final case class StartGameMessage(userId: UserId, sessionId: SessionId,
    caseId: CaseId, revision: Revision) extends APIMessage[StartGameResponse]
```

从 `CaseRevisionTable.find` 读出 `body` → `FixtureLoader.decode` → `FixtureRules.initial`。

**影响面**：`FixtureRules.initial/investigate/open/replay/view` 全部改成显式接收 `fixture` 参数；`MutationSupport` 与三个 Message 相应加载一次 fixture。这是本批的主要工作量。

### 3.5 `session` 表补齐案件引用列

```sql
CREATE TABLE IF NOT EXISTS game_sessions (
  session_id  VARCHAR(200) PRIMARY KEY,
  owner_id    VARCHAR(200) NOT NULL,
  case_id     VARCHAR(200) NOT NULL,
  revision    INT NOT NULL,
  body        CLOB NOT NULL,
  sequence_no BIGINT NOT NULL CHECK (sequence_no >= 0)
)
```

`case_id` / `revision` 必须上列（不能只埋在 `body` 里），否则"这个案件有几个存档"这类查询做不了。

### 3.6 `seedSamples`

照 `BookTableInitializer.seedSamples` 的模式加一个：若 `case_revisions` 为空，则导入 `docs/fixtures/minimal_investigation.json`。演示就从"读文件"变成"读库"。

### 3.7 验收

- `test` 全绿（`Failed 0, Errors 0`）
- `run` 的验收输出仍应包含 `[验收通过]`
- **额外自检**：手工给 `case_revisions` 表插一个 `revision=2` 的新版本，确认已有会话仍读 `revision=1`。这是本批的核心价值。

---

## 4. 批三：删事件溯源 + 合并记录（约半天）

这一批是缩减幅度最大的一批。做完之后 `games` 的领域概念从 8 降到 4。

### 4.1 删除清单

| 文件 | 说明 |
|---|---|
| `games/objects/FixtureChange.scala` | 变化 ADT |
| `games/objects/FixtureCommit.scala` | 提交包装 |
| `games/objects/FixtureInvestigationRecord.scala` | 并入 `SessionRecord` |
| `games/objects/FixtureAcquisition.scala` | 并入 `SessionRecord` |
| `games/tables/CommitTable/`（2 文件） | 日志表 |
| `system/objects/ReducerVersion.scala` | 随重放一起消失 |
| `FixtureRules.replay` / `applyChanges` | 约 30 行 |

### 4.2 合并成一个贯穿行与状态的类型

```scala
// services/games/objects/GameSession.scala
final case class GameSession(
  id: SessionId, owner: UserId,
  caseId: CaseId, caseRevision: Revision,
  sequence: Sequence,
  playerPlace: PlaceId, playerPosition: Vec3,
  opened: Set[EntityId], unlocked: Set[EntityId],
  visibleNodes: Set[SceneNodeId], completedOperations: Set[OperationId],
  clues: Set[ClueId], facts: Set[FactId]
)
```

这就是老师 `BookRecord` 的写法：**一个类型同时是数据库行、当前状态、内部模型。** 原 `StoredSession` 与 `FixtureState` 合并于此；`initialState` 字段不再需要（`FixtureRules.initial(fixture)` 是纯函数，开局时算一次直接写入）。

### 4.3 合并成一个追加记录类型

```scala
// services/games/objects/SessionRecord.scala
final case class SessionRecord(
  id: RecordId, session: SessionId, sequence: Sequence,
  point: PointId,
  route: Option[RouteId], operation: Option[OperationId],
  text: Text,
  clues: Set[ClueId], facts: Set[FactId]
)
```

对应领域文档的 `InvestigationRecord`（`route` / `operation` 最多一个有值）与 `InformationAcquisition` 的合并。一次玩家动作 = 一行。

### 4.4 新表

```sql
CREATE TABLE IF NOT EXISTS session_records (
  record_id   VARCHAR(200) PRIMARY KEY,
  session_id  VARCHAR(200) NOT NULL REFERENCES game_sessions(session_id),
  sequence_no BIGINT NOT NULL CHECK (sequence_no > 0),
  point       VARCHAR(200) NOT NULL,
  route       VARCHAR(200),
  operation   VARCHAR(200),
  text        CLOB NOT NULL,
  clues       CLOB NOT NULL,
  facts       CLOB NOT NULL
)
```

`clues` / `facts` 暂时存 JSON 数组。**若页 07 / 页 09 需要"某条线索来自哪次检查"这种反查**，再加 `session_record_clues` 子表（判据同第 7 节）。

`game_sessions` 同时加上 `state CLOB`（存合并后的 `GameSession` JSON），`sequence_no` 保留为独立列用于乐观锁：

```sql
ALTER TABLE game_sessions ADD COLUMN state CLOB;
-- 新库里直接建成：
--   body CLOB 由 state CLOB 取代
```

### 4.5 状态就地更新

`MutationSupport.commit` 改为：

```scala
for
  _ <- SessionTable.advance(connection, row.id, row.sequence, next, newState)
  _ <- SessionRecordTable.insert(connection, record)
  _ <- ReceiptTable.insert(connection, row.id, request, StoredReceipt(digest, result))
yield result
```

`SessionTable.advance` 的 `UPDATE ... WHERE sequence_no = ?` 返回 0 行即 `StaleState`——这一条乐观锁保留，它是幂等与并发的基础。

### 4.6 测试改造

现有 12 项里受影响的：

| 原测试 | 改成 |
|---|---|
| 日志版本／顺序及编解码 | 删除（没有日志了） |
| 文件数据库关闭后恢复 | 保留，改为读 `state` 列 |
| 后段失败完整回滚 | 保留，检查 `advance` 与 `SessionRecordTable.insert` 同事务 |
| 相同请求并发重试 | 保留，靠 `game_receipts` 主键 |

> **验收标准要改**：`minimal_backend.md` 现在写的是"Total 12 / Passed 12"，改完后**测试数量会变**。新的验收标准是 **`Failed 0, Errors 0`** 且 `[OK] test completed`，不要写成固定 12 项。

### 4.7 验收

- `test`：`Failed 0, Errors 0`
- `run`：`[验收通过]` 且退出码 0
- 手工：同一请求重试返回原结果（幂等未破）；不同 `expectedSequence` 报 `StaleState`（乐观锁未破）

---

## 5. 批四：前端 5 处小改（约 1 小时）

文件：`docs/frontend/frontend_types.md`

| # | 位置 | 改动 |
|---|---|---|
| F1 | `PresentationState` | **删掉三个分支里的 `stamp`**。现在 `ready.presentation.stamp` 与 `ready.snapshot.stamp` 是两个可以不同的值，类型上无约束——典型非法状态可表达。统一用 `snapshot.stamp` 一个来源 |
| F2 | `MutationAcknowledgement` | 后端 `CommandResult` 应加 `requestId: RequestId`。否则 `outcomeUnknown → 重试` 这条路径上，客户端无法自证收到的回执对应哪个 RequestId。同步更新 §6 的映射表 |
| F3 | `SceneView.models[].url` | 后端 `Asset.uri` 是 `fixture:placeholder-model`，浏览器加载不了。**建议删掉 `url`，只留 `assetId` + 前端本地注册表**（与 `visualPresetId` 的思路一致）；若保留，必须写明"只接受可加载的 http(s)" |
| F4 | `UiProblem` | 加 `code: string`（来自后端 `ErrorCode`），`recovery` 改由纯函数从 `code` 推导。现在丢掉错误码会导致：`StaleState` 不自动刷新、`ConditionFailed` 错误刷新、`RequestConflict` 被当成用户可见错误 |
| F5 | `InvestigationPageState.failed` | 保留上一次的 `snapshot` 与 `selection`（只读、不可操作）。现在三个非 ready 分支都丢 `snapshot`，一次网络抖动就清空用户选择 |

两条建议（非缺陷）：

- `AccessState.allowed` 加 `source: "remote" | "localDemo"`。后端还没有 HTTP 与鉴权，让"本地演示"在类型上可见，就不会被误当成已鉴权。
- `InquiryPointView` 补 `fallbackText?: string`，与后端 `fallbackFinding` 对齐。点到一个没有固定路线的入口时，前端目前无内容可显示。

同时在 `frontend_types.md` §6 的"现状 → 前端处理"表里补两行：`CommandResult` 缺 `requestId`、`storyTimeLabel` 无数据源。

---

## 6. 老师示例与 `rules.txt` 的冲突

| 冲突点 | 老师示例 | `rules.txt` | 采用 |
|---|---|---|---|
| 表目录命名 | `tables/book/BookTable.scala`（小写表名） | 规则 35：`tables/XXTable/XXTable.scala` | **跟规则**（现状正确，不动） |
| 表方法可见性 | `private[books]` | 规则 6/10：公开 Table 方法应能被整个后端调用 | **跟规则**。且批二之后 `games` 必须跨服务读 `case_revisions`，`private[cases]` 直接用不了 |

说明：同一份示例内部就与规则不一致，可见示例给的是**规模与粒度的经验基线**，不是逐条规范的权威版本。因此：规模、类型粒度、分层数量跟老师；表归属、可见性、Message 形态跟 `rules.txt`。

---

## 7. 判据：什么时候拆成独立列，什么时候整块存在一列

不是"JSON 列一定坏"。判据是**这份数据需不需要被单独查询、约束或部分更新**：

| 数据 | 做法 | 原因 |
|---|---|---|
| `owner_id` / `sequence_no` | 独立列 | 要按用户查、要做乐观锁 |
| `case_id` / `revision` | 独立列 | 要按案件查"几个存档"、要绑定修订 |
| 案件 `body` | 整块 CLOB | 从不按内部字段查，整体读整体写 |
| 会话 `state` | 整块 CLOB | 同上；`sequence_no` 已单独成列 |
| `text` | 独立列 | 要 `NOT NULL`、可能要做全文检索 |
| `clues` / `facts` 集合 | 暂 CLOB，需要反查时再拆子表 | 目前只整体读 |

对照老师的 `books`：他全部用独立列，因为每本书都是独立行、字段少、要按 `title`/`isbn` 查。我们的案件是一张大图，性质不同。

---

## 8. 明确不做

| 项 | 理由 |
|---|---|
| 改名（`Fixture*` → `CaseDef` / `RunState` 等） | 只影响可读性，不影响评分维度。**Circe 只用字段名建 JSON，类名不参与编码**，所以改名本身零风险、可随时做，但现在不做 |
| 拆 `Topic:Fact` 一对一 | `Claim.claimedValue` 需要唯一锚点 |
| 给 `Operation` 加条件化效果 / 物理仿真 | 领域文档 §25 已定：机关只做只读对象，走固定事实与预制演示 |
| 前端补齐审讯 / 指控 / 复盘页面类型 | 按 `frontend_types.md` §1 的暂缓策略，切片前再定 |
| `walkable` / `movement` 的运行期校验 | 当前固定观察位，不起作用；做导航时再补 |
| 把"静默丢弃"的字段全部改成显式拒绝 | 值得做但不在本轮；见第 9 节待办 |

---

## 9. 顺便记录：本轮识别但不在计划内的改动

以下问题真实存在，但优先级低于上面四批，记在这里避免遗忘：

1. **加载器静默丢弃一批已声明字段**：`assets`（资产白名单未校验）、`nodes[].asset`、`nodes[].represents`、`places[].walkable`、`scene.movement`、`routes[].method.label`、`operations[].label`、`places[].name`、`points[].label / subject / topics / fallbackFinding`、`facts[].topic / explanation / sources`、`title / initialStoryTime / timeOriginLabel`。
   → 其中**资产白名单**最危险：文档说"资源 URI 必须来自允许的资产目录"，代码里一条校验都没有。
2. **`FixtureLoader` 把所有解码错误压成一句 `InvalidValue`**，写真实案情时无法定位字段。
3. **`Text.fromString` 拒绝首尾空白**，多行文案以换行结尾会被拒。
4. **`GetGameStateMessage` 是只读查询却走 `lockOwned`（`SELECT ... FOR UPDATE`）**，应加非锁的 `findOwned`。
5. **前端需要的 `label` 三处（路线、操作、地点名）后端没有数据源**——批二/批三不会自动解决，写真实案情时要补。

---

## 10. 执行顺序与验收速查

```powershell
Set-Location 'D:\2026autumn\software_making\detective_scenic'
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 test
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 run
```

| 批 | 内容 | 预计 | 验收 |
|---|---|---|---|
| 一 | 删 `TopicId`、2 个 DTO、`FixtureCommand` | 30 分钟 | `test` 全绿 |
| 二 | `cases` 建表 + `StoredSession` 改引用 | 1.5 小时 | `test` 全绿；加 `revision=2` 后旧会话仍读 `revision=1` |
| 三 | 删事件溯源 + 合并记录 + 就地更新 | 半天 | `Failed 0, Errors 0`；`[验收通过]`；幂等与乐观锁手工复验 |
| 四 | 前端 5 处小改 | 1 小时 | 文档自洽，无遗留的 `stamp` 重复与 `url` 假设 |

**每批做完都提交一次 Git**，末尾附一句"本轮 `games` 领域类型数：N"。

---

## 11. 待作者决定

1. **连续两次改动会重建数据库**（批一改摘要算法、批二改表结构）。是否接受"旧演示数据作废"？建议接受——当前只有演示会话。
2. **`clues` / `facts` 用 JSON 列还是拆子表**？建议先 JSON，等页 07/09 真的需要反查再拆。
3. **批二是否顺带做批一**？两者都动 `MutationSupport`，合起来做可以少改一次签名。
4. **批三做完是否立即写真实案情**？建议先跑通玩具案件再写，避免案件数据被字段改动反复推翻。

## 12. 对照课程原文与教师样例的复核（2026-10-27）

本节是核对结果，不表示已批准或执行前述施工清单。本轮未改 Scala 源码、数据库或前端类型。总体判断：**减少同义包装、使用清楚的状态与记录、补案件修订存储的方向可采纳；按教师类型数量强制同构不成立，若干代码与迁移步骤必须先修正。**

### 12.1 已核实的教师实现

- 第1节的文件统计准确：books 有9个 Message、8个直接 objects 文件／7个类型定义、4个 DTO、2张表；user 对应5、5／4、2、2。TimeCodecs 是工具对象，不算领域类型。
- BookTable.decrementAvailable 确实执行带库存条件的 UPDATE，返回 IO[Boolean]；BookTableInitializer 确实将 initialize 与 seedSamples 分开。可借鉴这两个直接、易读的组织方式。
- **“一个类型贯穿行／状态／返回”不是老师的一般规则。** user/objects/StoredUser 含 passwordHash／passwordSalt；UserProfile 不含这些字段。LoginUserAPIMessage.profileFromStored 显式投影。图书 BookRecord 中的 status、categoryLabel 也由私有 readBook 派生，不是与物理列一模一样的模型。
- **BookSaveData 本身没有校验行为。** 它是无 codec 的内部参数收拢结构，被 CreateBookAPIMessage.validateSaveData 校验，并传给 BookTable.insert。不能由此得出所有输入类型不应有 codec，或对象内部应承载业务校验的规则。
- 借阅记录独立成表属实，但不是严格只追加日志：BorrowRecordTable.markReturned 会更新 status／returned_date。可以借鉴“不同生命周期的数据独立存放”，不要把它解释成教师要求某种事件模型。
- Table 方法 private[books]／private[user] 与现行 rules.txt 所允许的公开跨服务边界有差异，优先课程原文。**规则35说每表独立文件夹，XXTable 是示例，不明确禁止小写表目录。** 当前大写目录可保留，不能把老师的小写命名判成违规。

核对文件均位于 `../../../samples/samples/sample/backend-sample/src/main/scala/`；课程规则为 `../../../samples/samples/rules.txt`。老师的业务规模是参考，不是类型数、表数或分层数的验收上限。

### 12.2 可以采纳的部分

| 建议 | 结论与边界 |
|---|---|
| 删除当前源码未使用的 TopicId | 可以；只删当前切片死代码，不同时删除通用领域草案中的 Topic 概念 |
| 合并两个只有 result 字段的响应包装 | 可以，共用 CommandResult 满足“一 API 一个结果”；注意调用方由 response.result 改为 response，不能宣称完全没有接口形状影响 |
| cases 保存案件修订、会话绑定 caseId＋revision | 适合接下来多会话和选案；当前内嵌 fixture 也确实冻结了已存会话的版本，并非目前完全没有权威依据，只是重复存储且不利管理 |
| 当前状态＋调查记录＋请求回执 | 对首版侦探切片是合理的简化选项；纯规则仍计算下一状态，IO 边界事务保存；不需要为了纯函数而强制使用事件溯源 |
| 保留幂等、并发序号、访问判定 | 应保留，已有直接功能与测试消费者；不能因教师样例没有就删除 |
| 查询／约束所需字段独立成列 | 方向正确；集合或固定文档可类型化存 JSON，但须明确编码版本、列与文档的一致性 |
| Text 与 ID 区分校验、保留错误定位、补展示标签 | 都是实际问题，尤其标签和记录文本是下一条浏览器路径的输入，不应只放无限期待办 |

此前最小实现一次带入变化 ADT、提交、获知包装、重放等机制，增加了阅读路径。即使它们原先有设计依据，也应重新按首版实际用途评估；不以已经写完为理由维持所有层次。

### 12.3 不能直接执行的后端细节

1. **批一不是“零风险纯删”。** 摘要变更会影响已存回执，返回包装合并影响调用方。无需因为删 FixtureCommand 就必须改摘要格式：可以把当前固定 JSON 结构留在两个消息的摘要辅助函数中，保留旧回执兼容性。
2. **分隔符拼接摘要有碰撞前编码歧义。** 当前 ID 仅限制非空和首尾空白，冒号合法。Investigate(point=`a:b`, route=`c`) 与 Investigate(point=`a`, route=`b:c`) 都得到 target=`a:b:c`，相同外层字段时 SHA-256 完全相同。这是不同载荷不能被区分，不是破解哈希；应采用明确字段的 JSON／长度编码，而不是该模板。
3. **复合主键不保证修订不可变。** PRIMARY KEY(case_id,revision) 只保证唯一，UPDATE body 仍然合法。需插入后不覆盖的应用边界、重复导入冲突策略，以及会话引用完整性。可以增加会话到案件修订的复合外键；读取时也要验证列中的 ID／版本与文档一致。
4. **body 的含义前后不一致。** §3.1 说保存 InvestigationFixture JSON；§3.3 实际保存原始 CasePackage JSON。FixtureLoader.decode 目前只接收后者。必须选择并记录一种存储格式，不能交替写入。
5. **批二会引出 private[cases] 访问错误。** §3.4 若让 games 取得 String 后直接调用 FixtureLoader.decode，会违反当前 cases/utils 的可见性与课程边界。可由 cases 的公开 Table 返回已解析的类型化修订，或提供确实完成加载／校验的内部 Message；不要在 games 复制解析器或开放私有 utils。
6. **裸 String 表接口不利于本次“类型清晰”的目标。** 不是禁止数据库存 JSON，而是建议用有明确字段的修订记录／类型化返回值封装它；JSON 编解码及版本派发由 cases 负责。增加案件表本身不会自动解决旧 JSON 增加必填字段后的解码兼容。
7. **记录主键作用域写错了。** 现有 RecordId 按 RequestId 派生，并只要求会话内唯一；多个会话都可使用 request=`read`。若沿用该编号，§4.4 的 record_id 单列主键会冲突。应采用 (session_id,record_id)，或明确改为全局编号；一动作一记录时也应约束 (session_id,sequence_no) 唯一及 route／operation 互斥。
8. **新表数量并非目标表所写的3张。** 实际清单是 case_revisions、game_sessions、session_records、game_receipts，共4张；games 自己就有3张。不能为了与老师 user 的2张表相同而遗漏调查记录或回执。
9. **主行重复字段要明确一致性。** 合并后的 GameSession JSON 与外层 session_id／owner_id／case_id／revision／sequence_no 重复。写入必须来自同一对象，更新状态和序号在同一 SQL／事务完成，读取检查或明确以列为准；不能出现序号更新而 JSON 状态仍旧的情况。
10. **seed 不能仅检查整张表是否为空。** 已经有别的案件时，目标示例仍可能不存在。应针对指定 (CaseId,Revision) 幂等导入；相同编号不同内容拒绝覆盖，而不是清库或跳过全部初始化。
11. **改成只读查询不能直接移除当前行锁。** 现实现分两次读会话序号与日志，普通 READ COMMITTED 可能拼出不同时间点；FOR UPDATE 当前有一致性用途。换为单行权威 state 后可以普通 SELECT；保留多次查询时应选择一致快照／重试验证等方案。
12. **只剩 Failed 0／Errors 0 不能证明验收。** 还需确认目标测试套件实际执行、至少一项测试、保存／恢复／去重／冲突／权限／回滚等能力仍覆盖。测试总数不必固定为12，但不能因为删掉验证就判定通过。
13. **同 RequestId 改 expectedSequence 应仍报 RequestConflict。** 计划末尾“不同 expectedSequence 报 StaleState”仅适用于新 RequestId；已完成请求的原载荷重试必须先返回保存结果，不能被过期序号拦截。

### 12.4 删除重放的依据应重写

“没有按序号回放的页面”不等于重放没有消费者：当前 GetGameStateMessage 与存档恢复直接调用 replay，完整领域草案也记录了历史认知审计。保存／恢复是实现消费者，不只 UI 页面才算消费者。

但**这些需求并不强制全量事件溯源**。首版可以使用权威当前状态恢复，使用追加调查记录支撑日志和复盘；以后需要人物历史版本时，再明确记录认知 before／after 或相应快照。正确理由是“用更小的实现满足已确认能力”，不是“老师没有，所以删掉”。

迁移时需要：给现有会话重放一次生成当前状态；将玩家可见检查／获知记录转换为新调查记录；保留案件修订与原请求回执；验证迁移前后知识与权限一致。若确实选择新建演示库，应使用新的数据库路径、保留旧库，不能未经用户确认清空原文件。

删掉 reducerVersion 后，**状态和回执 JSON 仍需要格式版本及解码策略**，也不能暗中取消原先约定的历史信息能力。对这项架构调整，应同步修订领域契约和项目原则，再执行代码改动。

### 12.5 前端五项建议的复核

| 项 | 复核 |
|---|---|
| F1 删除 PresentationState.stamp | 可以减少状态字段，但原 stamp 是异步资源任务的关联标签，不是第二份业务真值。删除后仍必须保留回调／闭包中的快照标识核对，防止旧加载回调将新场景误标为 ready。不能只删字段就宣称完成修复。 |
| F2 回执加 requestId | 推荐，便于明确关联和诊断；“没有就无法关联”过强，HTTP 调用闭包也能关联。若加入已存 CommandResult，须处理旧回执缺字段的解码，不能假定兼容。 |
| F3 模型只留本地 assetId | 首个基础几何书房可以这样简化。现文档本来也允许空 models＋本地注册表，不是必然直接 fetch fixture: URI。以后加载 GLB 时仍可使用获准 URL；应支持经解析的同源相对资源路径，不强制所有输入必须是绝对 URL。 |
| F4 保留错误码、推导恢复动作 | 推荐保留后端业务码与传输／解码错误来源，未知码有保底。恢复动作还取决于请求是否可能提交、HTTP 状态及页面上下文，不能只按字符串 ErrorCode 机械决定重发。 |
| F5 失败保留旧快照 | 刷新时保留旧快照合理；首次加载失败本就没有旧数据。现 awaitingSnapshot 已保留 snapshot，只是缺刷新错误的明确承载，可局部补上。身份失效／切换账号时不得继续展示另一身份的旧资料。 |

localDemo／remote 来源显式化值得采纳。fallbackText 若增加，只能是当前获准公开的无发现提示，不能从隐藏路线中拷贝内容。storyTimeLabel 缺源判断正确，尤其纯投影丢掉初始剧情时间后不能让前端临场猜时间。

### 12.6 其他表述修正与建议顺序

- “只读机关”不是当前已确认的全局限制。领域草案 §25 区分固定事实、预制表现与自由操作计算；允许登记后的基础状态变化。不能将暂不做完整物理仿真扩大为禁止 Operation 读／改装置状态。
- “类名完全不参与 Circe 编码，改名零风险”过强。普通产品类型按字段编码，但 ADT 判别名、显式 codec、反射／路由命名、外部引用可能使用类型名。此次 Fixture case class 单独改名未必改 JSON，但仍应检查实际编解码与调用边界。
- 加载器“171行不能省”不是质量标准。现实现确实丢弃展示标签和部分语义引用；应把原始解码、引用校验与运行投影的责任说清楚，可以重写得更易读，不必保留原有行数。
- 案件内容校验和资产权限不能一概放到低优先级待办：加载标签是下一条网页路径的前置；资产白名单在真实资源入口启用前必须落实。当前尚未加载资源，应准确说明安全边界，不宣称已有资源校验。

建议顺序：先做低风险的死代码／同形响应整理，保持摘要兼容；补 cases 的类型化修订存储与必要展示字段；明确首版权威状态与调查记录的最小职责，再独立评估当前状态迁移；最后同步前端契约。各步按具体行为验收，不按“领域类型恰好12个”验收。

本节仅完成评审。未执行删除重放、重建数据库、改返回类型或重写前端类型，也未重新运行未改动的业务测试。

## 13. 按数据库表组织的类型方案

本节按用户提出的方式重排设计：**先确定表的业务职责，再定义对应记录，最后补操作输入、公开响应与表内的复合内容。** 范围是首版最小调查流程。它是目标结构草案，不是已执行的表迁移，也不因排成四张表就自动批准删除旧提交日志。

### 13.1 先看四张业务表

| 服务 | 表 | 对应记录类型 | 一行代表什么 | 写入方式 |
|---|---|---|---|---|
| cases | case_revisions | CaseRevisionRecord | 某案件的一个确定修订 | 发布／导入时插入，同修订不覆盖 |
| games | game_sessions | GameSessionRecord | 某位玩家的一局游戏及当前进展 | 创建一次，成功操作后原子更新 |
| games | session_records | SessionRecord | 这局里一次已成功完成的操作／检查 | 随操作追加，保留调查依据 |
| games | game_receipts | RequestReceiptRecord | 这局里某个请求已得到的成功结果 | 随操作插入，重复请求读取原结果 |

这些记录是后端内部类型。和教师的 StoredUser／UserProfile 一样，记录中含内部状态时，对外返回独立的安全视图。合并的是重复包装，不是把整张表交给浏览器。

### 13.2 案件修订表：保存“案件原本是什么”

| 列 | Scala 字段 | 含义 |
|---|---|---|
| case_id | caseId: CaseId | 案件编号 |
| revision | revision: Revision | 该案件的修订号 |
| content_schema_version | contentSchemaVersion: Int | body_json 的存储格式版本 |
| title | title: String | 展示标题，可直接查询 |
| body_json | content: CaseContent | 有确定类型的固定内容，整块编码保存 |

```scala
final case class CaseRevisionRecord(
  caseId: CaseId,
  revision: Revision,
  contentSchemaVersion: Int,
  title: String,
  content: CaseContent
)

// 案件记录的复合字段，不是另一种会话状态，也不是任意 JSON 参数袋。
// 子类型沿用当前 cases/objects 中的明确类型，展示字段在接浏览器前补齐。
final case class CaseContent(
  place: PlaceId,
  placeName: String,
  initialStoryTimeSeconds: Long,
  timeOriginLabel: String,
  spawnPosition: Vec3,
  nodes: Vector[FixtureNode],
  points: Vector[FixturePoint],
  facts: Vector[FixtureFact],
  clues: Vector[FixtureClue],
  routes: Vector[FixtureRoute],
  operations: Vector[FixtureOperation],
  initiallyOpened: Set[EntityId],
  initiallyUnlocked: Set[EntityId],
  commonFacts: Set[FactId]
)
```

body_json 固定存 CaseContent 的编码，不再含另一份 caseId／revision，也不混存原始 CasePackage JSON。导入文件仍可使用现有作者格式：读原文 → 校验 → 映射 CaseContent → 编码入库；原文件保留作制作输入。文件格式版本与内容存储版本分开，不直接硬编码同一个数字。

主键为 (case_id,revision)。不提供覆盖同修订内容的操作；重复导入相同内容可以返回已有修订，不同内容须报告冲突或创建新修订。CaseRevisionTable 的公开 find 返回 IO[Option[CaseRevisionRecord]]，编码、版本识别与私有行映射均由 cases 负责，games 不拿裸字符串调用 cases 的私有解析器。

此处 CaseContent 只覆盖当前调查夹具，不宣称代替包含人物／真实事件的完整 CasePackage。它内部的节点、线索和规则仍保留类型；不要求每个嵌套类型都建一张表。若后来需要跨案件单独查询、约束或更新某一类数据，再拆子表。

### 13.3 游戏会话表：保存“这一局现在怎样”

```scala
final case class GameSessionRecord(
  id: SessionId,
  owner: UserId,
  caseId: CaseId,
  caseRevision: Revision,
  stateSchemaVersion: Int,
  sequence: Sequence,
  storyTimeSeconds: Long,
  playerPlace: PlaceId,
  playerPosition: Vec3,
  opened: Set[EntityId],
  unlocked: Set[EntityId],
  visibleNodes: Set[SceneNodeId],
  completedOperations: Set[OperationId],
  clues: Set[ClueId],
  facts: Set[FactId]
)
```

| 列／列组 | 类型字段 | 存储约定 |
|---|---|---|
| session_id、owner_id | id、owner | 独立列，支持查会话与归属校验 |
| case_id、case_revision | caseId、caseRevision | 独立列，并以复合外键引用案件修订 |
| state_schema_version、sequence_no | stateSchemaVersion、sequence | 格式版本和并发序号 |
| story_time_seconds、player_place | storyTimeSeconds、playerPlace | 当前剧情时刻与地点 |
| player_x、player_y、player_z | playerPosition | 三个有限数值列，映射为一个 Vec3 |
| opened_json、unlocked_json | opened、unlocked | 明确的 EntityId 数组，解码为 Set |
| visible_nodes_json、completed_operations_json | visibleNodes、completedOperations | 明确的编号数组 |
| clues_json、facts_json | clues、facts | 当前已获知识编号集合 |

本方案把当前状态直接放在这条记录中，便于读懂、保存与查询。字段映射由私有 readSession／参数绑定函数完成，参考老师 BookTable 的组织；不用先还原多层包装才能看到一局当前状态。暂不再同时存一份包含相同字段的 state 大 JSON，减少独立列与副本分叉。

Set 的每列 JSON 都有明确元素类型，不接收自由键值；集合引用与案件修订一致。stateSchemaVersion 仍有必要：删掉重放不意味着以后改字段就自动兼容。

SessionTable.find 返回完整的内部 GameSessionRecord；网页通过 GetGameStateMessage 生成安全响应。SessionTable.update 应在同一个 UPDATE 中写入新状态与新序号，并以旧 sequence_no 为条件；owner、案件修订不得随普通调查修改。

### 13.4 调查记录表：保存“玩家这次做了什么、得到了什么”

```scala
final case class SessionRecord(
  id: RecordId,
  sessionId: SessionId,
  requestId: RequestId,
  sequence: Sequence,
  storyTimeSeconds: Long,
  point: PointId,
  route: Option[RouteId],
  operation: Option[OperationId],
  text: String,
  disclosedClues: Set[ClueId],
  disclosedFacts: Set[FactId]
)
```

字段分别对应 record_id、session_id、request_id、sequence_no、story_time_seconds、point_id、route_id、operation_id、text、disclosed_clues_json、disclosed_facts_json。主键为 (session_id,record_id)；首版一次成功动作一条记录，另约束 (session_id,sequence_no) 唯一。

首版只支持固定操作和固定检查：route 与 operation 必须恰有一个非空，由解码／业务验证与数据库 CHECK 共同保证。将来记录自由尝试或非空间交流时，再按实际消费者调整形状。

disclosedClues／disclosedFacts 表示这一次实际披露的信息，可以包含以前已获信息，不能把重复查看误当成第一次发现。GameSessionRecord 保存累计集合，SessionRecord 保存每次来源；两者职责不同。纯计算规划本次结果，再在同一事务里更新会话、追加记录及保存请求回执。

这里合并当前只有玩家一个接收者的 Investigated／Acquired 包装，不宣称覆盖未来 NPC 旁听、认知修订等场景。人物视角历史仍属于后续明确需求，不能因最小记录变简单而从通用领域契约删除。

### 13.5 请求回执表：保存“这个请求已经处理过”

```scala
final case class RequestReceiptRecord(
  sessionId: SessionId,
  requestId: RequestId,
  digest: String,
  responseSchemaVersion: Int,
  result: CommandResult
)
```

对应 session_id、request_id、digest、response_schema_version、response_json；主键为 (session_id,request_id)。result 是明确的已批准结果，不是任意响应对象。它和调查记录不同：前者用于重试原样返回，后者用于展示调查经过。

沿用当前无歧义的摘要格式，不因删除 FixtureCommand 而改字符串协议。修改 CommandResult 的必填字段前须为旧回执提供解码／迁移；未来 HTTP 回执可以在边界附带已知 requestId，不必为了增加该信息先破坏已有 response_json。

本表暂只记录同步事务中成功的请求；AI 长调用的 Pending 协议仍须另行设计。不能由这张表推导当前已实现异步任务系统。

### 13.6 围绕表定义操作与响应

| 操作 | 所读／写的表 | 返回 |
|---|---|---|
| ImportCaseMessage（内部） | 校验制作文件，写案件修订 | 案件与修订编号 |
| StartGameMessage | 读指定案件修订，创建会话 | 会话编号、初始序号 |
| GetGameStateMessage | 验证会话归属，读案件／当前会话／公开调查记录 | 同一序号的安全玩家视图 |
| ExecuteOperationMessage | 读会话和回执；计算后更新会话、写记录与回执 | CommandResult |
| InvestigateMessage | 同上，检查固定路线后保存结果 | CommandResult |

纯函数仍保留：validateCase、canAccessPoint、planOperation／planInvestigation 等接收明确对象，返回下一状态及本次记录；随机编号、文件／数据库、网络与真实时钟留在 IO 中。可以先用一个有清楚字段的内部 ActionPlan 收拢结果，只有出现实际需要才新增，不恢复一串仅转发的层。

公共 Table 方法保持课程要求的 IO[T]、封装 SQL；每表一个目录和 TableInitializer。消息中应能直接看见权限、回执、规划、持久化这条业务主线。独立消息可共用 CommandResult，不为每个消息再套一个只有 result 的壳。

### 13.7 与当前实现的对应及实施边界

| 当前类型／数据 | 目标职责 |
|---|---|
| InvestigationFixture | 拆出案件身份／版本字段到 CaseRevisionRecord，其余固定内容归 CaseContent |
| StoredSession＋FixtureState | 合并成 GameSessionRecord，恢复首先读取当前记录 |
| FixtureInvestigationRecord＋玩家 FixtureAcquisition | 合并为 SessionRecord 的本次披露信息 |
| StoredReceipt | 补齐明确行标识与格式版本，形成 RequestReceiptRecord |
| FixtureChange／FixtureCommit／replay | 若迁移到上述权威当前状态，先验证迁移一致性，再决定删除；迁移前保留 |
| 公开 DTO | 只保留与玩家实际需要不同的数据边界，不与内部完整记录强行共用 |

这是“按表组织”，不是“所有概念都等于一张表”。先看四张表就能理解案件、当前进展、调查经过和重复请求，再展开 CaseContent 里的内容类型。

本轮没有创建上述源码或执行 DDL。后续实现应先备份已有演示数据，迁移前后比较同一会话的知识、权限及调查结果，并保留原请求回执；不得以清库作为唯一验证。通用领域文档与项目原则的同步调整跟随实际架构决定，不因本节使用新名字就视为已经改完。


> 2026-10-27 实施同步：最小 Scala 切片已按四张表重构，具体实现及验收命令以 [minimal_backend.md](minimal_backend.md) 为准。当前状态直接存储；旧提交日志重放仅用于离线迁移。导入入口为 ImportCaseMessage，操作和调查直接返回 CommandResult，不再增加 result 包装。本文件中尚未实施的完整领域或接口仍是设计草案。
