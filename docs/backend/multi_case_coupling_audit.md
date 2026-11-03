# 多案件适配审计与改造清单

> 2026-11-03实施状态：本页第1—8节保留改造前审计证据，旧文件／行号不代表当前接口。修订号移除和清档已完成；本批实现、验证及剩余工作见第9节与[交接第13节](../handoff/handoff_1103.md#13-2026-11-03续接去除修订清理旧档与首批解耦)。

更新：2026-11-03。范围：现有后端、前端、案件导入／发布／存档路径及相关测试源码。

最新用户决定：案件修订号要从数据、数据库、API、前端和测试中移除，不能再用修订号判断任何业务逻辑；旧调查允许清空，不做历史兼容。独立验收案直接使用《斑点带子案》，不再新增静态小案。下文源码证据描述的是改造前现状，处理方案以本决定为准。

本轮只核对源码并提出分批改造方案，没有修改执行逻辑、数据格式或案件内容，也没有重新运行后端、浏览器或真实模型测试。此前194项后端测试通过是前一轮记录，不能充当本轮新增的验收结果。

## 1. 结论与目标

目前不是“只有一个案件ID能运行”：目录、发布记录、开局和存档已按案件／修订组织，人物与调查规则也已有配置入口。主要问题是通用流程仍夹带首案的表现、评分、时间及入口假设，且能力检查分散。

目标是让案件内容由数据决定，由明确登记的执行能力约束可玩范围。一个未支持的案件应获得具体的缺口报告，不能被静默裁剪或让模型自行补出执行能力。首案与《斑点带子案》改用统一结构。旧调查允许清空，不再保留修订链与历史行为分支；正常新调查仍须保证幂等和恢复。

本清单区分三种处理方式：

- **案件配置**：人物、事实、线索、地图、评分标准及表现要求从核心逻辑中移出。
- **引擎能力**：场景加载、时间推进、条件与效果等采用明确且经过实现的协议；不能只删掉白名单或放宽JSON。
- **清理历史机制**：移除案件修订号及为历史调查保留的接口、分支和测试，不用改名方式继续维持修订机制。

## 2. 已有基础：继续复用

| 能力 | 源码入口 | 审计结论 |
| --- | --- | --- |
| 案件与会话绑定 | [OpenInvestigationMessage](../../backend/src/main/scala/services/games/api/OpenInvestigationMessage.scala#L17) | 当前按caseId／revision查发布记录；复用案件隔离能力，去掉revision字段与相关键，不保留旧绑定协议 |
| 公开目录 | [CaseReleaseTable](../../backend/src/main/scala/services/cases/tables/CaseReleaseTable/CaseReleaseTable.scala#L30) | 当前选择每案最新修订；改为案件当前已发布内容的目录投影，保留草稿与公开目录隔离 |
| 导入与发布一致性 | [ImportCaseMessage](../../backend/src/main/scala/services/cases/api/ImportCaseMessage.scala#L14)、[RegisterPlayableCaseMessage](../../backend/src/main/scala/services/games/api/RegisterPlayableCaseMessage.scala#L21) | 保留重试和冲突处理原则；移除以修订号为键的机制。旧调查可清空，作者草稿、公开发布与活动调查的一致性另行明确 |
| 人物资料与警方身份 | [CharacterConfiguration](../../backend/src/main/scala/services/cases/utils/CharacterConfiguration.scala#L29)、[GetDialogueKnowledgeMessage](../../backend/src/main/scala/services/cases/api/GetDialogueKnowledgeMessage.scala#L14) | 新作者资料按独立人物／问询引用及dossier配置；无需重新建一套角色引擎 |
| 声明式操作与调查 | [FixtureRules](../../backend/src/main/scala/services/games/utils/FixtureRules.scala#L16) | 按配置查入口、前提、效果及材料，主要业务未写死雷丁或安妮的ID；可扩展协议 |
| 几何可达性 | [SceneAccessRules](../../backend/src/main/scala/services/games/utils/SceneAccessRules.scala#L8) | 已有按节点父链及距离判断的统一入口；目前仅静态平移能力，扩展时保持同一权威 |
| 短事务、租约与回执 | [AskWitnessMessage](../../backend/src/main/scala/services/games/api/AskWitnessMessage.scala#L54)、[FinishInterviewMessage](../../backend/src/main/scala/services/games/api/FinishInterviewMessage.scala#L28)、[MutationSupport](../../backend/src/main/scala/services/games/utils/MutationSupport.scala#L73) | 请求冻结、连接外调用、完成时复核及提交已有基础；不要为通用化绕开 |
| 浏览器选案与恢复 | [investigationConnection](../../frontend/src/features/investigation/utils/investigationConnection.ts#L19) | 当前新开局带案件／修订，旧路径回退OpenStudy；新流程只选案件，删除旧回退与revision载荷，保留新调查正常恢复 |

## 3. 现有“第二案”测试究竟覆盖什么

本节是读取测试实现的结论，不是本轮重新执行的结果。

| 测试 | 实际构造与覆盖 | 未覆盖 |
| --- | --- | --- |
| [CaseHubSuite：setup](../../backend/src/test/scala/services/games/CaseHubSuite.scala#L28)、[第二案测试](../../backend/src/test/scala/services/games/CaseHubSuite.scala#L96) | 复制修订21记录，只改变caseId与标题；验证发布、开局、进度隔离、只读列表及主动查看解答 | 不同真相／实体／节点／证据链，不是新案完整推理成立并Solved结案 |
| [CharacterConfigurationSuite](../../backend/src/test/scala/services/cases/CharacterConfigurationSuite.scala#L112) | 从首案源修改案件ID、修订、部分人物资料和警方问询ID；检查私情隔离、冻结、重复导入与冲突拒绝 | 使用内部StartGame／AskWitness，不证明公开目录到新场景及结案全链；仍保留首案主要结构 |
| [SemanticInterviewSuite](../../backend/src/test/scala/services/games/SemanticInterviewSuite.scala#L175) | 首案副本换案件ID并增加同义材料，验证按事实声明解析材料与实际出示 | 不同案件逻辑、人物集合及完整结案 |
| [浏览器case-hub](../../frontend/tests/case-hub.spec.ts#L13)、[第二次调查](../../frontend/tests/case-hub.spec.ts#L48) | 固定进入vicarage_study，再新开另一会话并恢复原会话 | 是同案多局，不是两个不同案件的浏览器游玩 |

这些测试有保留价值，但不能把复制首案记录当作通用案件已验收。独立验收案确定为《斑点带子案》，不额外补一个静态案件。它在同一案件内分阶段验证输入、人物与证据、场景、连续事件、结案及上传流程；阶段通过不冒充全案已完成。

## 4. 首案耦合清单

### C01：通用视图仍由StudyProjection承担

- **证据：**[StudyProjection](../../backend/src/main/scala/services/games/utils/StudyProjection.scala#L8) 同时包含首案ID／修订集合、场景支持判断和通用调查投影；[GetInvestigationSnapshotMessage](../../backend/src/main/scala/services/games/api/GetInvestigationSnapshotMessage.scala#L25) 全部进入该对象。
- **实际边界：**有发布记录时走release绑定，已经允许其他caseId；不能误报“所有新caseId都被拒绝”。无发布记录的旧路径才按首案集合回退。
- **归属与处理：**通用快照投影归games；删除首案修订集合与旧OpenStudy回退。开局按案件选择已审核内容和已登记能力，不再携带案件修订号。
- **验收：**案件隔离与发布权限成立；数据、API和前端不含案件修订号，代码没有对应阈值或集合判断，旧回退入口被移除。

### C02：场景能力检查分散且检查层次不同

- **证据：**[supportsScene](../../backend/src/main/scala/services/games/utils/StudyProjection.scala#L14) 只支持根包study的4／5／6；[FixtureLoader.validateScene](../../backend/src/main/scala/services/cases/utils/FixtureLoader.scala#L280) 对地点支持study/5、woodland/1，另固定停留点数与坐标范围；[前端sceneSchema](../../frontend/src/apis/games/objects/InvestigationSnapshot.ts#L22) 又列一次支持集合。
- **影响：**根发布包和子地点能力不是同一层；新增一个渲染能力要同时修改多处，容易出现能导入却不能开局、能开局却前端解码失败。不能简单把这三个列表合并为完全相同的一张列表。
- **归属与处理：**登记发布内容、子场景资源、布局范围和可执行效果之间的关系；校验所有引用。cases校验通过公开API／数据契约取得能力结论，不跨服务调用games私有utils。保留严格输入检查。
- **验收：**已支持组合可导入、发布、开局并被前端接受；未知包、子场景或资源在发布前给出具体诊断。不得把Zod改成任意字符串来冒充支持。

### C03：场景响应含固定实体状态，缺少通用实例表现契约

- **证据：**[StudySceneView](../../backend/src/main/scala/services/games/objects/apiTypes/StudySceneView.scala#L7) 强制drawerOpened／windowOpened；[投影](../../backend/src/main/scala/services/games/utils/StudyProjection.scala#L50) 按EntityId("drawer")／("window")取值；[前端校验](../../frontend/src/apis/games/objects/InvestigationSnapshot.ts#L22) 每种场景都要求两个布尔值。
- **影响：**无抽屉的新案仍携带这些字段；节点位置与几何资源没有作为通用安全视图下发，现有响应不能直接支撑模型生成的新场景。
- **归属与处理：**案件保存稳定实体／节点绑定和已登记表现要求；后端输出获准的当前状态；前端按已登记的资源与能力加载。新契约使用有约束的实体关联与表现状态，不引入任意JSON或模型脚本。
- **验收：**没有drawer／window的新案不受隐式实体名影响；同一渲染资源可绑定不同实例ID；状态与合法调查入口一致。旧调查和回执不作兼容验收；只检查改造后新调查的一致性与恢复。

### C04：现有渲染器固定首案几何、节点与材料ID

- **证据：**[StudyScene](../../frontend/src/pages/Scene/components/SceneViewport/StudyScene.tsx#L33) 写死几何和位置，钟面在[68行](../../frontend/src/pages/Scene/components/SceneViewport/StudyScene.tsx#L68)固定为6:22；[StudyNodes](../../frontend/src/pages/Scene/objects/StudyNodes.ts#L2) 固定节点；[WoodlandScene](../../frontend/src/pages/Scene/components/SceneViewport/WoodlandScene.tsx#L34) 以clue_woodland_sample决定样本表现；[SceneViewport](../../frontend/src/pages/Scene/components/SceneViewport/index.tsx#L78) 固定两种画面与首案标签；[相机记忆](../../frontend/src/pages/Scene/objects/SceneCameraMemory.ts#L3) 只区分study／woodland。
- **归属与处理：**现有study／woodland几何与资产可复用，但不为旧调查保留多套表现分支；新模板以登记部件和实例绑定连接调查实体，不能依赖首案节点名。之后模型生成蓝图仍需通过资产、布局及状态检查。相机按会话、地点与场景资源区分，避免同模板多地点互串。
- **注意：**当前前端schema会先拒绝未知场景；不能声称现在未知包必然误画书房。但如果只扩大schema而保留“非woodland即StudyScene”的分支，就会产生这种错误。
- **验收：**独立实例改变节点、实体、钟面及场景文字后画面一致；取得材料与实体移除分别定义，动画不自行授予材料。无WebGL入口遵循相同权限。

### C05：推理行为由案件修订号阈值选择

- **证据：**[EvaluateReasoningMessage](../../backend/src/main/scala/services/games/api/EvaluateReasoningMessage.scala#L79) 与[ReasoningRules](../../backend/src/main/scala/services/games/utils/ReasoningRules.scala#L115) 使用revision >= 19决定成立后是否跳过引导调用以及使用固定反馈。
- **影响：**内容相同的新案修订1和修订19会执行不同模型流程；这是首案开发历史，不是通用修订号含义。
- **归属与处理：**删除案件修订号字段与相关判断，统一评审流程；确有产品差异时使用含义明确的行为配置，不用另一数字替代修订号。当前请求仍冻结实际输入，旧请求允许清空。
- **验收：**模型调用与反馈只受明确配置和当前状态影响；不再有revision字段、阈值或旧案件行为分支，正常请求重试仍幂等。

### C06：固定五组评分跨校验、计算与前端展开

- **证据：**[FixtureLoader](../../backend/src/main/scala/services/cases/utils/FixtureLoader.scala#L238) 强制五组及五个不同标签，即使配置了语义评审也仍检查；[ConclusionRules](../../backend/src/main/scala/services/games/utils/ConclusionRules.scala#L22) 固定人物50分与每组10分；[前端响应校验](../../frontend/src/apis/games/objects/InvestigationSnapshot.ts#L72) 重算每组10分；[Conclusion页面](../../frontend/src/pages/Conclusion/index.tsx#L15) 固定100分和五组说明。
- **归属与处理：**按产品实际需求确定评分与语义评审的职责。标准数量、材料分组及必要权重归配置；不为旧分数兼容保留五组限制或额外协议分支。
- **验收：**新案用2／3／7项合法标准或材料分组均按相应协议处理，前后端一致；未知能力明确拒绝；不检查旧五组分数或旧复盘兼容。

### C07：无警方的案件可能被导入／发布，却无法完成通用操作

- **证据：**`CaseContent.police`为可选，[人物校验](../../backend/src/main/scala/services/cases/utils/FixtureLoader.scala#L344) 只检查存在时引用有效；但[等待](../../backend/src/main/scala/services/games/utils/ExternalCheckRules.scala#L32)、[旧结案](../../backend/src/main/scala/services/games/utils/ConclusionRules.scala#L19)、[评审保存](../../backend/src/main/scala/services/games/utils/ReasoningRules.scala#L105)及[查看解答](../../backend/src/main/scala/services/games/utils/ReasoningRules.scala#L128) 都借警方point记录日志。
- **影响：**“结案、等待”与“向警方交流”混成一个入口要求；《斑点带子案》前期没有常驻警方，不能只换警官名字解决。
- **归属与处理：**警方交流仍需显式身份与权限；通用评审、等待及系统记录使用自己的行动上下文。明确调查入口日志和系统事件日志的区别，不捏造一个警方NPC／point。实际解耦前，发布能力检查应识别这种不可完成组合。
- **验收：**无警方新案可按获准流程等待、评审和明确收尾；警方接口在没有接触对象时仍拒绝；日志不伪装玩家到过某处。

### C08：剧情时间固定从周五解释

- **证据：**[ExternalCheckRules.timeLabel](../../backend/src/main/scala/services/games/utils/ExternalCheckRules.scala#L10) 固定星期起点；[InvestigationClock](../../backend/src/main/scala/services/cases/objects/investigation/InvestigationClock.scala#L5) 只有日内分钟与统一动作耗时；[InvestigationApp](../../frontend/src/app/InvestigationApp.tsx#L47) 没时钟时回退“周五上午”。
- **归属与处理：**案件提供日／历法原点和显示精度，允许只写“第1日”；引擎维护相对剧情时间。动作耗时与未来节点处理另行接入，不拿地图估算或模型耗时冒充实际剧情耗时。
- **验收：**不同星期及未设星期的案件显示正确，跨午夜正确；刷新、切页和模型等待不推进剧情。首案若仍设为周五，应由其实际配置表达。

### C09：地图算法可复用，页面默认值与布局提示仍属首案

- **证据：**[MapPage](../../frontend/src/pages/Map/index.tsx#L7) 固定道路标签偏移、study／old_hall默认地点及圣玛丽米德标题；[estimateRoute](../../frontend/src/pages/Map/functions/estimateRoute.ts#L2) 实际按传入图计算，无首案ID依赖；[地图校验](../../backend/src/main/scala/services/cases/utils/FixtureLoader.scala#L456) 的坐标范围对应固定1200×700表现模板。
- **归属与处理：**起终点从当前可用地点选择，标题与特殊偏移归地图表现配置；固定画布可以继续作为已登记模板能力，不把尺寸常量一律判为案件耦合。保留图算法与“估算不代表已走过”的语义。
- **验收：**地图没有study／old_hall时仍有有效选项；同案多地点切换正常；图上距离与实际行动时间分开，不因页面选择而移动人物。

### C10：页面依首案人物与场景决定导航和说明

- **证据：**[PolicePage](../../frontend/src/pages/Police/index.tsx#L20) 按valet／stone／hartnell／archer筛联系人，并在[30行](../../frontend/src/pages/Police/index.tsx#L30)专门链接安妮；[InvestigationApp](../../frontend/src/app/InvestigationApp.tsx#L58) 只在woodland显示“返回牧师寓所”；[CasePage](../../frontend/src/pages/Case/index.tsx#L21) 默认警方提供医学初查，[37行](../../frontend/src/pages/Case/index.tsx#L37)仍提时间核对。
- **归属与处理：**后端获准视图提供接触对象、地点和可用业务动作，页面使用实际名称及配置文案。不要把同样的首案人物ID判断迁到另一份“通用工具”里。
- **验收：**新案不叫安妮、没有树林／医学意见／时间核对时，导航和文案符合实际；已有材料、问询与重试入口不消失。

### C11：作者源格式仍是特定执行剖面，运行投影不能替代完整作者稿

- **证据：**[FixtureLoader.decode](../../backend/src/main/scala/services/cases/utils/FixtureLoader.scala#L134) 只接受schemaVersion2及InvestigationOnly标签，要求events、claims、scheduled等为空；[186行](../../backend/src/main/scala/services/cases/utils/FixtureLoader.scala#L186)拒绝非空历史。`InvestigationOnly`标签并不等于当前游戏没有结案：已实现行为另由conclusion／evaluation承载。节点在[43行](../../backend/src/main/scala/services/cases/utils/FixtureLoader.scala#L43)只支持平移；[CaseRevisionTable](../../backend/src/main/scala/services/cases/tables/CaseRevisionTable/CaseRevisionTable.scala#L20)保存的是CaseContent投影。
- **归属与处理：**统一改造后的解码入口，不为旧作者文件保留多套分支；作者格式明确区分草稿、审核内容和可执行投影。按能力编译，完整保留原输入及未支持字段并报告；不要简单放开旧schema非空数组或把旧标签改成草案里的Scored。
- **验收：**《斑点带子案》作者稿可原样保存、审核并逐步编译到实际实现的能力；缺口未完成时准确报告，不能假装直接导入。平移／旋转／缩放等限制需与实际引擎能力一致。

### C12：启动过程硬编码首案导入与发布清单

- **证据：**[WebDemo](../../backend/src/main/scala/services/system/api/WebDemo.scala#L25) 固定导入首案文件和修订21—26发布记录；它是演示初始化，不是运行时通用案件目录。
- **归属与处理：**演示种子与正式案件登记分开；后续新案走公开授权的作者流程及内部发布API，不要求编辑应用启动代码。移除旧修订种子及多修订发布逻辑，只维护当前案件内容；仍保留必要冲突检查，不在启动时无声覆盖内容。
- **验收：**添加已审核新案不改WebDemo即可进入目录；重启保留当前发布内容与改造后的新调查；首案种子重复运行仍幂等、冲突仍可见。

### C13：提示中的具体例子与评审主题需按协议审查

- **证据：**[EvidenceAwareInterviewPrompt](../../backend/src/main/scala/services/games/utils/EvidenceAwareInterviewPrompt.scala#L10) 含安妮的亲属例子；[FocusedInterviewPrompt](../../backend/src/main/scala/services/games/utils/FocusedInterviewPrompt.scala#L12) 含牧师叫医生例子，已说明不能成为每人的经历；[ReasoningRules.judgePrompt](../../backend/src/main/scala/services/games/utils/ReasoningRules.scala#L35) 额外强调责任、动机、手法、时间与证据五类主题。
- **归属与处理：**统一当前提示流程，清理仅为历史调查保留的模板分支；实际请求内容仍需冻结。模板采用案件无关的示例，并让作者criteria决定评审要求。具体名字作为例子不等于代码已向NPC授权该事实，不能仅凭字符串出现就判定泄密。
- **验收：**新案不会把示例当本人经历；没有时间谜题的案件不被额外强求时间手法；旧冻结请求允许清空，不再要求历史模板兼容。需针对性模型实测，类型／单元测试不能证明正文无补造。

## 5. 历史机制删除清单与仍需保留的正常保障

| 位置 | 当前用途 | 最新处理要求 |
| --- | --- | --- |
| [OpenStudyMessage](../../backend/src/main/scala/services/games/api/OpenStudyMessage.scala#L12) | 旧请求不带案件定位，默认打开首案修订 | 删除旧入口和浏览器回退，统一按案件开局 |
| [LegacyDialogueKnowledge](../../backend/src/main/scala/services/cases/utils/LegacyDialogueKnowledge.scala#L14) | 为首案3—9修订补原人物上下文 | 删除修订依赖及旧文件回退，案件资料统一存入当前结构 |
| [LegacyPoliceProfile](../../backend/src/main/scala/services/cases/utils/LegacyPoliceProfile.scala#L23) | 兼容缺警方ID的旧结构 | 删除旧格式推断，新结构显式指定身份 |
| [InterviewContext](../../backend/src/main/scala/services/games/objects/InterviewContext.scala#L10)及历史模板分发 | 恢复旧调查、旧模板及旧答复格式 | 当前请求输入仍冻结，历史兼容分支不再保留 |
| 事务、提交序号、请求ID、租约和回执 | 保证正常新调查的一致性、并发、幂等与恢复 | 继续保障；它们不是案件修订号，也不能被拿来变相恢复修订机制 |
| 文本长度、材料数量、JSON大小等上限 | 资源与协议边界 | 按实际能力检查，不一律删除，也不与案件修订号绑定 |

用户允许清空的是改造前的调查历史；作者资料、密钥、用户ZIP及无关数据不能因此一并删除。实际清理范围在执行时列明，本轮尚未清理数据库。

`CharacterProfile.victimRelation`及页面“与死者的关系”、非空culprits等仍是当前犯罪调查的领域假设。若扩展未遂、无死者或意外责任评审，需要明确契约；不能强制用户填写Other或虚构死者。多人责任已有Set形态，不应误报当前只能有一名凶手。

## 6. 新时间结构的接入点：不是本批重构已完成的功能

| 接入点 | 当前结构 | 后续要求 |
| --- | --- | --- |
| N01 作者时间与历史 | 新CaseTimeline有事件与计划，但历史实际时刻仍为精确CaseTime；作者样本保留未知精度 | 区分作者已知的时间范围和运行必需的触发时刻；不伪造历史精确秒 |
| N02 世界与人物知情 | [GameSessionRecord](../../backend/src/main/scala/services/games/objects/GameSessionRecord.scala#L9)无新TimelineState；[SceneAccessRules](../../backend/src/main/scala/services/games/utils/SceneAccessRules.scala#L10)读固定节点；[InterviewRules](../../backend/src/main/scala/services/games/utils/InterviewRules.scala#L23)按固定问询点找人物 | 接入实际位置、生命状态和逐人获知；WitnessProfile是旧冻结投影，人物关联可从CasePersonBrief反查，不能假设PersonId等于WitnessId；新协议要明确关联并冻结 |
| N03 统一时间推进与提交 | [afterAction](../../backend/src/main/scala/services/games/utils/ExternalCheckRules.scala#L39)仅处理报告；[ActionPlan](../../backend/src/main/scala/services/games/objects/ActionPlan.scala#L9)单记录；[SessionRecord](../../backend/src/main/scala/services/games/objects/SessionRecord.scala#L9)必须带point；[MutationSupport](../../backend/src/main/scala/services/games/utils/MutationSupport.scala#L85)每次提交一条记录 | 按中间节点形成纯执行计划，系统发生与玩家日志分开；保持短事务、幂等及实际披露；不得先执行完玩家动作再把事件粗略补在末尾 |
| N04 格式与恢复版本 | [CaseRevisionTable](../../backend/src/main/scala/services/cases/tables/CaseRevisionTable/CaseRevisionTable.scala#L33)和[SessionTable](../../backend/src/main/scala/services/games/tables/SessionTable/SessionTable.scala#L56)当前只读版本1，后者更新要求序号恰加1 | 去掉案件修订号并统一内容／状态／提交语义，旧调查按用户授权清空；不做旧档迁移。会话提交序号仍用于并发与幂等，不是案件修订号 |

《斑点带子案》的持续观察条件、容器内部位置、灯光状态及未覆盖分支，继续按[作者样本的缺口表](../cases/speckled_band.md#101-正常输入)处理，分批在同一案件中验证，不另设静态小案前置任务。

## 7. 修正后的实施顺序：同一案件分步验收

不再额外创建静态小案；以下各阶段均使用《斑点带子案》，首案用同一新结构检查多案隔离。

### B1：删除案件修订机制并统一运行入口

移除案件内容、发布记录、会话关联、API载荷、前端展示／校验和测试中的案件修订号，清理按修订号分流及历史兼容入口。不将revision改名为caseVersion等继续保留原机制。旧调查允许清空，正常新调查的事务、幂等和恢复仍要成立。

退出条件：静态搜索与接口检查确认修订号机制已去除；案件通过caseId定位；首案和《斑点带子案》的输入不依赖修订字段。具体能力未实现时明确报告，不要求此时已完成夜间全流程。

### B2：以《斑点带子案》的白天部分解除内容与场景耦合

接入独立人物、事实、陈述、证据、地图和模型制作的场景绑定，处理无常驻警方、非首案节点ID、非周五原点等需求。复用资产允许，硬套首案实体和内容不允许；不为这一步另外编一个案件。

退出条件：该案白天调查、人物知识隔离、实际取证及状态保存成立。阶段通过只记为白天部分通过，不宣称案件已完整可玩或已经结案。

### B3：接同案夜间连续事件并完成案件流程

接入N01—N04及持续观察、实际位置、世界变化和系统动作上下文，运行已有原作主路径及指定测试变体。

退出条件：跨时刻顺序、条件取消、同室不同感知、异地不知情、取证门槛成立；实际干预与后续结果一致；从开局到明确结案、复盘跑通，失败、重试及真实进程重启不重复执行。未覆盖分支必须补充或明确边界，不能擅自改写原作。

### B4：用同一作者稿验收上传、审核与发布

接入表单／文件、私有草稿、模型审核、编译报告、预览与显式发布。上传成功后同一案件可进入完整游玩流程；不另建一套绕过引擎检查的上传通路，不重新引入案件修订号。作者内容修改如何影响正在进行的新调查，需在实现时明确规则，但不能借此恢复多修订机制。

## 8. 独立验收案的条件与记录方式

“独立”指内容与引用不依赖首案，不要求静态，也不要求另外写一个更小的故事。《斑点带子案》就是本项目选定的独立验收案。

| 条件 | 本案的验收要求 |
| --- | --- |
| 独立内容 | 自己的人物、真相、证据链、地点和实体ID，不借首案NPC、材料或默认节点成立 |
| 完整链路 | 作者稿最终能保存、审核、生成获准场景、开始调查、取证问询、推进事件、结案与复盘 |
| 可检查的结果 | 已发生／未发生、实际观察者、取得材料及世界变化有明确预期，不以模型一句“通过”作为验收 |
| 真实干预 | 原作主路径和已声明变体分开，干预能够影响计划，不能强行播放既定结局 |
| 范围明确 | 同一案分白天、夜间、上传阶段实施；阶段结果不冒充全案验收 |
| 多案隔离与恢复 | 与按新结构运行的首案互不串档，新调查重试、并发及真实重启保持一致；旧调查不做兼容验收 |

每个改动记录问题、当前行为、目标行为、实际检查及未完成项。按改动运行必要的编译／测试和端到端验证；模型正文质量需针对性实测。仅文档更新时不启动全套服务。

本轮完成审计与方案修正，尚未实际删除代码中的修订号、清空旧调查或实现新运行流程。

## 9. 首批实施结果

- **C01完成本批拆分**：通用投影改为`InvestigationProjection`，删除首案caseId／修订集合和OpenStudy回退。案件、发布和会话统一按caseId关联；实际场景实例仍见C03。
- **C02部分完成**：作者源`scene.bundleId`明确给出根场景，地点和发布绑定均不含修订；支持能力仍严格限定study／woodland，几何及跨端能力登记尚待改造。
- **C05修订分流已删除**：评审成立统一使用固定结案邀请，不再按案件编号或修订选择评审逻辑。作者criteria仍决定具体核对内容；提示模板中首案例子及历史协议清理属C13后续。
- **C06已完成组数解耦**：加载器、纯评分、前端协议与页面不再固定五组；按已覆盖组比例计算共50分。语义评审可不配置旧覆盖组；普通覆盖模式仍要求至少一组。
- **C12部分完成**：启动只导入当前案件并幂等发布，不再播种多修订；开放作者导入／发布流程尚未实现。
- 已删除旧迁移API、表适配器、旧修订人物资料回退和仅验证旧存档兼容的测试。保留独立内容配置测试与正常调查的权限、事务、幂等、并发、模型等待和恢复测试。
- 旧本机调查、请求和回执已清理；新调查的保存与重启恢复重新验证，未通过清空新档处理验收失败。

**下一批**：C03通用场景实例与渲染绑定、C07系统动作上下文去掉警方point依赖、C08时间原点、C04几何、C09地图、C10导航与C11作者源契约，以及C13人物协议默认推断。《斑点带子案》继续作为独立验收案；当前尚未可玩。N01—N04仍未接入运行时，不应把新类型和本批清理算作连续事件已实现。

## 10. 第二批实施：通用场景状态与系统动作

- **C03视图契约完成**：SceneView提供获准可见对象与openedEntities，删除固定抽屉／窗户字段；书房动画通过节点→作者绑定实体→通用状态取值。隐藏对象状态不会混入视图。C04几何、资源和节点布局仍待改造，不声称任意案件已经有自己的场景。
- **C07通用系统动作完成**：SessionRecord的point改为Option，增加InvestigationSystemAction；等待、评审、普通评分与结案记录不含point／route／operation。数据库约束保证来源互斥，不将系统动作混为某人的发言或现场调查。无警方配置可运行这些动作；专门的警方交流、由警方受理的专业核查继续执行各自权限。
- 新增任意实体ID、隐藏与异地状态不披露、无警方等待／评审／两种收尾、原请求重试、未获材料拒绝、身份保护、文件恢复及存储约束检查。已有新调查的结构调整保留内容、序号和回执，不清档。
- 下一批为C08时间原点、C09地图默认值与C10导航说明，再推进C04场景绑定／几何及C11作者输入；连续事件N01—N04仍待接入。继续用《斑点带子案》，没有新增独立故事取代它。

## 11. 第三批实施：时间、地图与导航

- **C08时间显示完成**：作者可提供公历原点、仅星期或相对原点。公历按剧情秒0及作者偏移量计算；星期按开局weekday跨日／跨周；未提供就不推定星期。原点进入存储与统一时间标签，导入校验不一致和越界。新连续事件仍未接入。
- **C09默认值与首案标签坐标已解除**：实际地点列表提供初始选择，当前案件标题提供地图名称，通用算法放置道路距离文字；固定地图视口属于现有渲染能力，尚未扩为任意版式。
- **C10导航与提示已解除首案假设**：按当前公开能力显示入口，保留直接书签空态；删除特定包名对应的剧情说明、医生报告假设、安妮快捷链接和指定联系人名单。实际作者配置及已登记几何仍归案件内容／C04。
- 验证及剩余边界见[交接第15节](../handoff/handoff_1103.md#15-2026-11-03续接时间原点通用地图和导航说明)。下一批为C04、C11及C13；连续事件N01—N04尚未实现。

## 12. 白天独立案件已接入，C04新增受检数据通路

《斑点带子案》白天阶段已经以自身人物／实体／事实／地点运行，不再只是首案副本改名。`authored`能力用SceneBlueprint登记受检基础几何，运行时不执行模型脚本；节点、实体和操作完全按作者绑定，当前地点实例由后端授权投影。同模板不同地点的相机键分离，树林样本状态不再依赖首案线索ID。

白天普通调查3分钟、移动5分钟，阶段至17:20，后续23:00窗口只展示，不跳过未执行事件。完整白天取证、问询、笔记和恢复可验证；夜间、作者输入自动编译和上传仍待接入。C04旧模板参数化仍有剩余，不能因新增数据渲染器就宣称所有已有几何已解耦。实际范围及验证见[白天试玩说明](../cases/speckled_band_daytime.md)与交接第16节。
