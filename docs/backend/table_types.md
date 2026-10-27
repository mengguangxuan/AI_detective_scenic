# 当前后端：按表阅读类型

这是已实现的最小调查切片，完整人物、审讯和案件生成类型仍待后续扩展。先看四张表，再看表中复合内容；不要把每个类型都理解成一张表。

## 1. 案件修订 case_revisions → CaseRevisionRecord

| 字段 | Scala 类型 | 含义 |
|---|---|---|
| caseId | CaseId | 案件编号，与 revision 共同唯一 |
| revision | Revision | 内容修订号；修改内容创建新修订 |
| contentSchemaVersion | Int | 存储格式版本，当前为1 |
| title | String | 案件标题 |
| content | CaseContent | 类型化固定内容，数据库存 body_json |

CaseContent 包含地点编号和名称、初始剧情时间及时间原点说明、出生位置、场景节点、调查入口、事实、线索、调查途径、可执行操作，以及初始打开／解锁对象和公开事实集合。它是复合字段，不是另一张表。当前投影仅支持单地点固定调查。

## 2. 当前会话 game_sessions → GameSessionRecord

| 字段 | Scala 类型 | 含义 |
|---|---|---|
| id / owner | SessionId / UserId | 会话编号与所属用户 |
| caseId / caseRevision | CaseId / Revision | 引用冻结案件修订 |
| stateSchemaVersion | Int | 当前状态格式版本，当前为1 |
| sequence | Sequence | 已成功提交的动作序号，用于并发校验 |
| storyTimeSeconds | Long | 相对案件时间原点的剧情时间；当前操作不推进时间 |
| playerPlace / playerPosition | PlaceId / Vec3 | 玩家所在地点与位置 |
| opened / unlocked | Set[EntityId] | 已打开／解锁对象 |
| visibleNodes | Set[SceneNodeId] | 当前可见场景节点 |
| completedOperations | Set[OperationId] | 已完成操作 |
| clues / facts | Set[ClueId] / Set[FactId] | 玩家累计获准知道的信息 |

这是当前状态的权威来源。位置按坐标列存储，集合使用类型化 JSON 列；不再把案件和初始状态塞入会话大 JSON 后重放。

## 3. 调查记录 session_records → SessionRecord

| 字段 | Scala 类型 | 含义 |
|---|---|---|
| id / sessionId / requestId | RecordId / SessionId / RequestId | 记录、所属会话及来源请求 |
| sequence / storyTimeSeconds | Sequence / Long | 成功动作序号与剧情时间 |
| point | PointId | 调查或操作的入口 |
| route / operation | Option[RouteId] / Option[OperationId] | 调查途径或现场操作，数据库约束恰有一个 |
| text | String | 本次固定结果或操作说明 |
| disclosedClues / disclosedFacts | Set[ClueId] / Set[FactId] | 本次披露的信息，区别于累计知识 |

主键为 (sessionId, id)，不同会话可以使用相同记录编号；同一会话序号唯一。失败请求不产生成功调查记录。

## 4. 请求回执 game_receipts → RequestReceiptRecord

| 字段 | Scala 类型 | 含义 |
|---|---|---|
| sessionId / requestId | SessionId / RequestId | 联合主键 |
| digest | String | 结构化请求 JSON 的 SHA-256 摘要 |
| responseSchemaVersion | Int | 原响应格式版本，当前为1 |
| result | CommandResult | 原成功响应，供相同请求重试直接返回 |

CommandResult 包含 sequence、findings 和 acquiredClues。相同编号但载荷变化时报冲突；新请求序号过期时报状态过期。

## 辅助类型与执行

ActionPlan 是纯规则产生的临时计算结果，包含下一会话状态、本次记录和响应，不对应表。Message 负责流程和事务内校验；Table 负责 SQL；utils 内规则负责纯计算。状态、记录和回执在同一事务提交。

Record 是后端内部存储类型，不等于前端响应。玩家只接收 GetGameStateResponse 和获准的 CommandResult；不能直接下发 CaseContent。

旧库迁移类型集中在 objects/migration，旧表只读适配器集中在 tables/migration，不属于正常玩法模型。启动及验收见 [minimal_backend.md](minimal_backend.md)。


## 5. 本机身份 local_identities → LocalIdentityRecord

accounts 新增的技术表，不是案件内容或玩家业务状态。userId 是服务器分配的身份编号；tokenHash 是随机凭证的 SHA-256 摘要。原令牌只通过 HttpOnly Cookie 交给浏览器，不存明文、不下发到业务视图。ResolveLocalIdentityMessage 在路由边界验证凭证，其他消息只接受转换后的可信 UserId。此实现仅面向本机演示，不是正式登录系统。

调查页读取 GetInvestigationSnapshotMessage 的公开组合视图，创建／恢复使用 OpenStudyMessage；原有 GetGameStateMessage 仍供内部最小测试使用。新增 API DTO 不是新业务表。


当前 CaseContent 增加 observations: Vector[SceneObservation]（node、label、description），只承载公开外观。FixtureOperation 增加 repeatable 和 openedRequirement: Option[EntityOpenRequirement]；后者包含实体编号与要求的 opened 布尔值。SetOpened(entity, opened) 与 SetNodeVisible 是封闭的程序效果，不穷举犯罪或调查内容。累计完成集合继续保留历史；当前能否执行由纯函数 operationAvailable 判断，不从标签文字推测。未增加业务表。
