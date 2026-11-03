# 当前后端：按表阅读类型

本文说明核心存储关系；问询、警方与评审另有请求和结果表。2026-11-03已移除案件修订机制，当前实现与验证见[交接第13节](../handoff/handoff_1103.md#13-2026-11-03续接去除修订清理旧档与首批解耦)。

## 1. 案件 cases → CaseRecord

| 字段 | Scala 类型 | 含义 |
|---|---|---|
| caseId | CaseId | 案件唯一编号 |
| contentSchemaVersion | Int | 存储格式版本，当前为1 |
| title | String | 案件标题 |
| content | CaseContent | 类型化固定内容，数据库存 body_json |

CaseContent 包含地点编号和名称、初始剧情时间及时间原点说明、出生位置、场景节点、调查入口、事实、线索、调查途径、可执行操作，以及初始打开／解锁对象和公开事实集合。它是复合字段，不是另一张表。sceneBundle显式绑定已登记表现能力，sceneLocations可配置书房／树林地点；通用场景实例仍待解耦。

## 2. 当前会话 game_sessions → GameSessionRecord

| 字段 | Scala 类型 | 含义 |
|---|---|---|
| id / owner | SessionId / UserId | 会话编号与所属用户 |
| caseId | CaseId | 关联当前案件，已有内容不同的重复导入拒绝覆盖 |
| stateSchemaVersion | Int | 当前状态格式版本，当前为1 |
| sequence | Sequence | 已成功提交的动作序号，用于并发校验 |
| storyTimeSeconds | Long | 相对案件时间原点的剧情时间；操作按案件时钟配置推进时间 |
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
| point | Option[PointId] | 现场调查、问询或操作的真实入口；系统动作为空 |
| route / operation | Option[RouteId] / Option[OperationId] | 现场记录恰有一个；系统动作均为空 |
| systemAction | Option[InvestigationSystemAction] | Wait、EvaluateReasoning、SubmitConclusion或RevealReasoning；与现场入口互斥 |
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

旧调查、请求与回执已清理，旧迁移类型和适配器已删除。新调查仍保证事务、幂等和重启恢复。


## 5. 本机身份 local_identities → LocalIdentityRecord

accounts 新增的技术表，不是案件内容或玩家业务状态。userId 是服务器分配的身份编号；tokenHash 是随机凭证的 SHA-256 摘要。原令牌只通过 HttpOnly Cookie 交给浏览器，不存明文、不下发到业务视图。ResolveLocalIdentityMessage 在路由边界验证凭证，其他消息只接受转换后的可信 UserId。此实现仅面向本机演示，不是正式登录系统。

调查页读取 GetInvestigationSnapshotMessage 的公开组合视图，创建使用 OpenInvestigationMessage(sessionId, caseId)，恢复使用 GetInvestigationSnapshotMessage；原有 GetGameStateMessage 仍供内部最小测试使用。新增 API DTO 不是新业务表。


当前 CaseContent 增加 observations: Vector[SceneObservation]（node、label、description），只承载公开外观。FixtureOperation 增加 repeatable 和 openedRequirement: Option[EntityOpenRequirement]；后者包含实体编号与要求的 opened 布尔值。SetOpened(entity, opened) 与 SetNodeVisible 是封闭的程序效果，不穷举犯罪或调查内容。累计完成集合继续保留历史；当前能否执行由纯函数 operationAvailable 判断，不从标签文字推测。未增加业务表。

## 6. 发布 case_releases → CaseReleaseRecord

按caseId关联cases，保存summary、tags与sceneBundle，不保存修订字段。公开目录只返回已发布案件的简介；内部发布验证当前案件内容、已登记场景能力和绑定一致性。同一案件相同导入／发布可以重试，不同内容拒绝覆盖。

格式标识contentSchemaVersion、stateSchemaVersion与回执responseSchemaVersion描述编码；提交sequence、租约和请求ID维护事务与幂等。它们都不选择案件内容或替代案件修订。

## 7. 通用场景授权视图 SceneView

`SceneView`替代首案专用StudySceneView。`objects`仍为当前可见且获准的节点／实体绑定；`openedEntities: Set[EntityId]`只包含这些对象中已打开的实体，不公开全部会话opened集合。未出现在objects的状态不能下发，前端同时检查成员和唯一性。删除drawerOpened／windowOpened字段。

书房渲染器通过已登记节点查对象绑定的entity，再读取openedEntities实现抽屉／窗户动画；几何和节点布局仍属书房模板，尚未成为任意场景生成器。隐藏祖先、异地与未登记实体的开合状态不下发。

等待、评审、普通评分提交和明确结案不依赖police或虚构PointId。记录表约束保证现场来源和系统来源互斥；已有新调查的记录表可调整结构，保留原记录内容、会话序号、请求摘要和成功回执。模型等待、幂等与事务路径保持原机制；有明确联系人授权的警方交流和专业核查仍需相应入口。

## 8. 案件时钟与日期

CaseContent可选calendarOrigin以带偏移的公历日期记录剧情秒0；不提供日期则不得补造。InvestigationClock仍配置开局startMinutesOfDay与每步actionSeconds，并可用startWeekday=1—7明确开局星期。两种日期依据同时存在时须相互一致；不提供星期／日期则按作者timeOriginLabel与相对天数显示。

日期标签、日志时间和核查期限共用时间计算；已保存回执和日志正文保持原文。当前为分钟级显示和既有核查推进，不代表新的连续事件调度器已运行。

## 9. 白天试玩的表现与时间配置

CaseContent.sceneBlueprint可选，配合登记的authored能力使用。房间按PlaceId登记floorSize／颜色／相机和公开说明；SceneVisual按SceneNodeId登记ScenePrimitive，形状有限为Box、Cylinder、Sphere。SceneView.render只包含当前地点及获准对象对应的节点合成位置与部件，不下发整个作者蓝图。

InvestigationClock可配置operationDurations（按OperationId指定秒数）、playableUntilSeconds（当前开放阶段截止）及targets（公开规划时刻，不是执行计划）。通常行动仍用actionSeconds，显式等待按提交分钟推进；现实中停留、阅读和模型网络耗时不按秒折算。MutationSupport在状态／日志／回执写入前检查阶段截止，模型问询准备也提前检查。ClockView给出maximumWaitMinutes、phaseEnd与只读目标，客户端不能借等待越过未开放阶段。

这些字段不能代替CaseTimeline、实际事件状态或人物知情更新。当前斑点带子仅运行截止17:20的白天调查；23:00目标只作安排展示，不执行未来信号、移动或袭击。
