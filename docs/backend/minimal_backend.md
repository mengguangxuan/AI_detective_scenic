# Scala 最小调查流程

> 2026-10-28更新：CLI最小夹具仍按本文运行；网页现已接入《牧师寓所谋杀案》首阶段。当前后端25项流程测试与2项物品登记测试均已通过，原著案范围、验证和手动路径见[前端实现说明](../frontend/implementation.md)开头的最新章节。下文早期“无网页／未接正式案情”属于CLI阶段的历史说明。

已实现一个本地 Scala／JDBC 调查切片。输入沿用 `docs/fixtures/minimal_investigation.json`；本次不新增正式案情、3D 网页或 AI 审讯。

## 启动与测试

Windows／PowerShell 推荐直接使用原生脚本，不依赖 python／python3 命令：

```powershell
Set-Location 'D:\2026autumn\software_making\detective_scenic'
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 doctor
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 test
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 run
```

以上 Bypass 只作用于该次 PowerShell 进程，不修改系统长期执行策略。doctor 只查找环境，不下载、不编译；应显示 Java 路径、启动器路径、版本和 `[OK]`。本机已在原生 Windows 验证找到 `C:\Program Files\Java\jdk-21\bin\java.exe` 与 IntelliJ Scala 插件启动器。

有真正 Python 解释器时也可用 `python tools/backend_sbt.py test`／`run`；Windows 下会转入同一 PowerShell 入口。VS Code 直接运行该 Python 文件、不传参数时只执行 doctor，不会自动开始测试。PowerShell 中的 python3 可能是 WindowsApps 应用别名，不能根据“命令存在”判断 Python 可用；优先采用上面的原生入口。

WSL／Linux 在项目根目录运行：

```bash
python3 tools/backend_sbt.py doctor
python3 tools/backend_sbt.py test
python3 tools/backend_sbt.py run
```

工具不下载启动器；首次运行 sbt 会下载未缓存的依赖。SBT_LAUNCH_JAR 可以指定启动器，DETECTIVE_JAVA 可以指定 Java 可执行文件；Linux 可用 DETECTIVE_BUILD_CACHE 指定缓存目录，默认 backend/.build-cache/linux，避免临时目录消失后缓存链接失效。Windows 复用本机 sbt 默认缓存，不复制整份缓存到临时目录。

也可在 IntelliJ 中打开 backend 作为 sbt 项目；但不要让 IDE 自动导入、WSL 和 Windows 终端同时构建同一工作目录。

### 验收标准

1. doctor 输出 `[OK] Environment located`（Windows），确认执行的是 Java，而不是无输出的 Python 应用别名。
2. test 最后包含 `Passed: Total 21, Failed 0, Errors 0, Passed 21` 与 `[OK] test completed`。
3. run 显示：提前阅读为 `Left(当前无法访问该调查入口。)`；获得“信上写着：今晚九点见。”；相同请求重试为 `true`；恢复后的 sequence 为 2，含 clue_letter 与 fact_letter_text，最后出现 `[验收通过] 调查、请求去重、关闭数据库后恢复均成功。`。

提前阅读的 Left 是预期的权限拒绝，不是演示失败。当前无网页可打开，验收对象是这条 Scala／数据库流程，不是视觉场景。

Windows 日志保存在 backend/.build-cache/windows-test.log 与 windows-run.log。卡住或失败时保留完整日志；首次下载时终端会先显示环境与日志位置，随后才是 sbt 输出，不应把尚未显示测试结果视为通过。

### 本次 FileSystemException 的原因与修复

实际检查到 backend/target/out 下的 inc_compile_3.zip 是指向 WSL `/tmp/detective-sbt/...` 的符号链接。Windows 无法通过该链接读取编译缓存；此前在 WSL 跑通并不能证明同一输出目录可以直接给 Windows 复用。

启动器现在记录输出平台；发现未知输出或切换 Windows／Linux 时，将 target/out、target/streams、project/target 移到 backend/.build-cache/output-backups/，不删除源码或 target/fixture-demo 中的数据库，然后重新生成当前平台输出。各平台的归档分开保留；不支持两边同时构建。直接绕过入口在 IDE 里构建前，也应确保当前缓存属于该平台。

已在 Windows 原生 PowerShell 实际完成诊断、旧缓存归档、重新编译、全部 21 项测试和演示。启动 JVM 与 fork 出的演示／测试 JVM 均显式使用 UTF-8，中文信件及错误提示已实际显示正确。

当前使用本机已有的 sbt 2.0.9、Scala 3.9.0 及 Java 21；Cats Effect 与 Circe 版本沿用课程样本。H2 是本阶段嵌入式数据库选择，MUnit 用于自动测试；不是把课程后端改为 Python。

演示先拒绝提前阅读信件，随后打开抽屉、阅读固定内容、以相同请求编号重试，最后关闭连接重新恢复会话。输出显示线索内容、序号及恢复状态。数据库使用一次解析后的绝对路径，保存在 backend/target/fixture-demo/windows/db-v2.mv.db（Windows）或 linux/db-v2.mv.db（Linux）。每次演示创建新会话，关闭前后比较完整状态；恢复连接使用 IFEXISTS=TRUE，不允许默默创建新数据库。旧的 backend/target/fixture-demo/db.mv.db 保留，不自动迁移或删除。

## 课程规范的落实

- 全部生产 Scala 代码在 src/main/scala/services/ 下；cases 管最小案件输入，games 管会话／调查，system 管消息基类、通用值及 JDBC 事务和应用入口。
- 每个数据类型独立文件，使用 final case class；编解码在同名伴生对象；ID 是独立类型，含 Map 键编解码。开放事实内容保留 Text。
- 纯规则在 games/utils，夹具解析规则在 cases/utils，均使用 private[所属服务] 可见性；不跨服务调用私有 utils。
- 每个操作独立 Message，构造参数承载输入，入口为 plan(connection: Connection): IO[Response]；响应放 games/objects/apiTypes。没有转发用 Service 层。
- 每张表有独立目录与 TableInitializer；SQL 私有并与公开 IO 方法相邻。消息不拼 SQL。SessionTable 私有查询辅助方法不对外接受任意 SQL。
- 消息在外层 DatabaseSession.transaction 中执行。写入先锁会话、校验 owner、检查相同请求回执，再校验预期序号、纯规划，最后同事务写入日志、会话序号和回执。无 AI／网络等待混入事务。

system/api 中的 DatabaseSession 和 FixtureDemo 是共享事务与应用启动边界，符合课程对基础设施归属的要求；不是领域对象或业务 Service。ImportCaseMessage 是制作期内部读文件入口，不接受浏览器提交路径。

字段、类型和职责逐项对照见 [table_types.md](table_types.md)。

## 持久化与恢复

| 表 | 对应类型 | 职责 |
|---|---|---|
| case_revisions | CaseRevisionRecord | 冻结案件修订；复合内容 CaseContent 存为类型化 JSON |
| game_sessions | GameSessionRecord | 当前状态的权威记录，按列存储序号、位置、知识和完成操作 |
| session_records | SessionRecord | 每次成功动作及该次披露的信息，不重复整份当前状态 |
| game_receipts | RequestReceiptRecord | 请求摘要与原响应，支持幂等重试 |

案件通过 caseId + revision 关联，会话不复制案件。常规查询直接读取当前状态，不重放历史。纯函数返回 ActionPlan，事务共同保存状态、调查记录与回执。案件修订只有插入和查询接口；修改内容必须创建新修订。

旧演示库保留。可运行 `powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 migrate`：先备份源库，再以只读方式打开，校验旧日志并迁移到 db-v2；重复运行核对已有结果，不重复插入。旧格式适配器位于 tables/migration 与 objects/migration，只有读取接口，因此不提供建表初始化器。旧日志不进入日常查询路径。

本切片没有长时间操作，因此不持久化 Pending：会话行锁串行化短事务，同一事务保存成功回执；失败完整回滚。若以后接入 AI，必须另行落实短事务登记 Pending／释放连接／调用模型／短事务复核，不得直接沿用此同步事务模型。

同 RequestId 同载荷返回原响应，即使 expectedSequence 已经过期；同 ID 不同载荷拒绝；新 ID 携带旧序号拒绝。不同新请求对同序号竞争只允许一个提交。成功调查通过纯规则计算本次披露的事实和线索，并在同一事务更新当前知识。

## 范围与限制

这是完整 CasePackage 的**最小调查投影** CaseContent，不宣称已实现整个领域初稿。Fixture* 类型是阶段实现，不替换文档中的通用案件类型。加载器明确拒绝非 InvestigationOnly、未来事件、人物、评分、非 Fixed 路线、不支持条件／效果和带旋转／缩放的节点；当前只有一间房，节点支持父链平移与半径距离检查。不支持跨地点移动或通用矩阵变换。

它校验所消费的运行字段和引用，不是完整案件发布校验器。文档 Python 校验仍检查原始完整 JSON 的形状。占位模型没有被渲染，视觉验收没有完成。

CLI 仍由可信本地调用方提供 userId。新增浏览器入口已通过 accounts 本机凭证校验构造可信 UserId，再调用已注册消息；未实现正式账户登录。HTTP 与前端启动见 [联网说明](../frontend/implementation.md)，不能将本机演示身份当作公网账户方案。

## 验证结果

15 项测试覆盖访问控制、线索获知、去重、载荷冲突、过期序号、事务回滚、双连接竞争、当前状态与本次披露区分、文件恢复、连续20个会话恢复、协议拒绝、案件修订冻结、跨会话记录编号及请求摘要边界。数据库结果仅针对 H2。

## 恢复失败不是正常验收结果

用户曾报告：前两步成功，但关闭连接后报“无法访问该会话”并以退出码1结束。这是失败；不能将提前阅读的预期权限拒绝与最终恢复失败混淆。复查时未复现该单次失败，原数据库中也未找到该会话，不能据此宣称已确定或彻底修复其原因。用户确认只运行了一个 PowerShell，暂无依据将其归因于并发构建。

随后加固：打印固定的数据库绝对路径、按操作系统区分演示文件、恢复时 IFEXISTS=TRUE、比较关闭前后完整状态、明确打印成功／失败标记。新增回归测试在同一个文件数据库连续创建20个会话，每次关闭连接，重新打开后核对所有既有会话。此测试在原生 Windows 通过，但加固与通过测试不等于已解释此前单次失败。

若更新后再出现失败，保留完整 windows-run.log；其中应包含数据库路径及待恢复会话编号。出现 `[FAILED]` 或非零退出码均不得判为通过。


新增联网切片有6项 HTTP 测试，与原15项基线共21项；覆盖安全玩家投影、身份／来源／输入边界、请求去重与冲突、文件数据库及 HTTP 重启恢复、并发新建幂等。CLI 演示路径不变，网页使用单独的 target/web-demo 数据库。实际跨进程浏览器恢复另见前端说明。


## 自由问询与独立模型客户端

当前网页案件修订为 `vicarage_study/3`，场景仍为 `study/4`。固定问答继续可用；自由问题通过 `AskWitnessMessage` 处理，资料来自显式的 `WitnessProfile`／`WitnessAnswer` 初查投影，不加载整份小说案情。人物与答复编号分别使用 `WitnessId`、`WitnessAnswerId`。模型只返回当前允许的答复ID，用户看到的是作者核对过的自然证词，未知与失败各有明确处理，不接收模型自由编造的正文。

模型接入独立在 `services/models`：`ChatCompletionClient.complete(ChatCompletionInput): IO[String]` 是外部传输契约，`DeepSeekChatClient` 实现Chat Completions格式。该接口不是浏览器API，不接收数据库连接；它允许替换服务或注入测试客户端。用户配置放在忽略提交的 `backend/config/model-api.local.json`，示例为同目录 `model-api.example.json`。只使用现有JDK HTTP客户端，无新增依赖。填写步骤见[验收准备第8节](../frontend/acceptance_preparation.md)。

### 长请求的事务边界

公开问询采用 `RegisteredAPIMessage.authenticatedWorkflow`：先在短事务解析可信身份，再进入问询编排。同步消息仍由 `transactional` 包装，保持原有事务要求。

1. `AskWitnessMessage.plan(connection)` 锁会话，检查归属、请求摘要、原回执、序号与出示材料，生成仅含当前人物可知材料的上下文，登记持久化Pending及90秒租约。
2. `AskWitnessMessage.run` 释放准备阶段连接后调用独立模型客户端。它是为数据库资源边界增加的执行入口，不是只做转发的Service层。
3. `FinishInterviewMessage.plan(connection)` 重新锁会话，校验租约令牌、截止时间、案件修订和当前序号；合规结果在同一短事务写入人物对话、调查日志、权威状态、请求状态和成功回执。取消也在提交后才转换成HTTP错误，避免回滚取消状态。

两张新增表为 `interview_requests` 和 `interview_turns`，分别维护待处理／完成请求及玩家可见对话。已有四张业务表和accounts本机身份表继续使用，不删除旧数据库或存档。同步操作不能占用已经用于问询的RequestId。处理中重复请求返回503并保留原载荷；已完成请求复用成功回执。租约过期可由相同请求重新接续，令牌阻止旧调用覆盖新结果；外部模型调用本身不承诺跨进程崩溃的恰好一次计费。

### 知识和输出边界

上下文只包含当前人物的已配置答复、该人物实际出示过的材料（本次、先前自由问询及固定出示记录），以及该人物最近8轮对话。玩家问题和材料中的说法作为待理解数据，不升级为真相。固定出示通过路线的 `presentedClues` 显式登记，不能根据中文标签猜测。

模型结果只接受严格的 `answerIds` JSON，最多两个不同、当前允许的ID；多余字段、其他人物ID或未知ID均回退，原始文本不下发。线索授予再检查原调查路线的前提，不能靠模型直接授予阶段结论。没有把引用合法等同于语义匹配一定正确：模型仍可能答非所问，真实模型的匹配质量需配置密钥后人工验收。

快照新增安全人物目录和已保存对话，不下发未取得答复、私人上下文、密钥、模型原始输出或提示词。前端会话定位键更新为v5以使用当前案件修订；旧修订留在数据库。问询请求在调用前保存原载荷，网络结果不确定时仍按原请求重试。


## 警方交流与初查材料（2026-10-28）

`CaseContent.police: Option[PoliceContact]`登记联系人公开身份和调查点，旧最小夹具可不提供。医生的死因／时间初查意见、现场说明和自首情况仍按固定路线取得；没有自由模型补充医学推断。`FixtureLoader`验证警方调查点存在且不与自由问询人物混绑。网页案件修订4，旧内容不覆盖。

`ReportCluesMessage.plan(connection)`接收受认证用户、会话、请求ID、预期序号及1至20个材料ID；先锁定拥有的会话，读回执，校验序号、材料已取得、警方入口可访问。只含已出示材料的新请求拒绝；相同原请求则返回原回执。出示不会授予材料或改变案件真相。

`police_exchanges`按会话／请求保存 `PoliceExchangeRecord`（序号、确实出示的ID、作者确认回复）；独立Table和Initializer封装SQL，启动仅新增本表。交流记录、原有调查日志、当前序号及请求回执在调用方同一短事务提交。日志使用记录标识 `police_exchange`，与已存在的人物问询记录标识一样，不是可直接调用来授予线索的作者路线。

玩家快照在会话行锁下读取，新增可空 `PoliceDeskView`，仅提供公开联系人、累计实际出示ID及本会话交流；不把玩家全体材料自动同步给警方。前端 `ReportCluesAPI`沿用原载荷持久化、未知结果原请求重试、成功后只补读视图的流程。与牧师、玛丽的模型对话／知识记忆互相独立。模型接口和真实本地配置不变。

新增4项后端测试覆盖初查材料取得与误用入口、所有权／未知材料／空列表／重复及冲突、强制回滚、并发重复和文件库重开；浏览器验收覆盖实际HTTP出示、丢响应后的原请求续接、重复与未得材料拒绝、刷新及窄屏。


## 自然对话协议与警方模型问询

新准备的问询上下文有 `responseVersion=2`，人物资料增加可空知识数组，默认值与解码兼容旧记录。独立作者文件按人物提供自身经历，不授予新执行能力。只有后台允许的披露ID可触发既有固定路线；新协议拒绝额外JSON字段、未知／其他人物ID、重复或超过4个ID、空白／超2000字／异常控制字符正文。普通自然答复可不授予材料；回复会按原样作为人物说的话保存，不能声称引用校验等于语义真实性校验。

已持久化的旧请求缺省 `responseVersion=1`，续接时保留旧匹配提示、answerIds协议及作者正文，不把已发出请求改成另一问题。成功旧回执优先于再次调用。保存的历史对话不改写。两种版本仅是有限的请求恢复协议，不是领域内容枚举。

警方使用同一AskWitness／FinishInterview，不另建不带权限的模型代理。病例与警方初查可通过警方自己的披露项取得；本局其他NPC对话不传给警方。准备阶段读取以前固定登记的PoliceExchangeTable记录，以实际出示为范围补上下文；完成时有成功人物答复且附了材料才同事务插入警方交流记录。正文和元数据各司其职；模型不会执行搜查、鉴定或结案。

模型网络仍在数据库连接外，租约／版本／序号／fencing校验及原回执重试不变。公开视图增加警方作为对话人物，不下发作者补充、完整模型提示或原始模型输出结构。


## 新问询人物、公开关系与对外策略（2026-10-29）

案件修订5新增两个调查点、人物档案、8份按来源标识的披露材料和相应固定／出示路线。所有模型授予仍沿用前提、角色和实际出示校验。`StudyProjection`支持旧修订3、4和当前5共用场景4，OpenStudy只给新会话选择5，不覆盖原会话。公开人物信息来自 `publicInfo.background.persons`，使用PersonId及可空问询WitnessId关联，加载器校验唯一性、文本和引用；玩家快照只投影公开字段。

对话补充升级为版本2的DialogueDossier，分别有knowledge、privateContext、policy。GetDialogueKnowledgeMessage只读取所选人物，AskWitness在新请求中冻结这些资料。WitnessProfile解码为旧记录补空私密／策略数组，因此旧已保存上下文和回执可恢复，不重新生成或换协议。privateContext与policy不下发玩家；自然输出仍可能违反角色策略，不能把类型或披露ID校验当成完整语义防泄漏。

前端问询从当前快照人物动态生成，警方仍单独进入警方页。CasePersonBrief仅含身份及公开关系，无供词正文或隐藏关系。第一轮完成后的nextObjective是公开的下一步提示，不改变已保存完成标志，不冒充第二阶段推理评分或全案结案。


## 正式会面入口与已发生陈述（修订6）

FixtureOperation增加requiredClues，持久化解码缺省为空；有限CluesKnown条件与原有All／EntityOpen并列，加载器验证非空与ID引用。安排安妮会面位于警方点，要求已取得雷丁首供，执行现有SetNodeVisible效果开放无几何的逻辑节点；操作不可重复，原请求回执可重试。没有新增任意脚本或真实外部通讯。

点访问使用现有祖先可见性检查，因此隐藏期间快照、Investigate和AskWitness都不能取得安妮的陈述或模型上下文。公开人物摘要的可选WitnessId在投影时按实际可接触目录过滤；身份关系一直公开，不提前提供会面入口或认罪材料。开放节点与取得陈述是两个独立状态变化，不能把一次进入页面当成已经认罪。

本局安妮陈述通过既有模型披露ID或固定路线保存。警方两份供述能力要求真正出示两个材料，雷丁／马普尔的回应同样绑定实际材料。开局前的历史仍固定，后续改口、正式报告、警方执行结果或结案没有自动发生。旧修订5及3／4受支持，不覆盖其案件内容。


## 2026-10-29 独立核查阶段

StudyProjection新建修订7并继续支持3—6，GetDialogueKnowledgeMessage增加男仆／斯通／哈特内尔的隔离资料。复用CluesKnown、SetNodeVisible和已持久化completedOperations／visibleNodes提供两个明确联系操作；不新增数据库表、自由动作协议或3D节点模型。未联系时猜测人物／路线ID被拒，模型不调用；已联系但未问询时没有证言。

新增10份材料及其出处，警方三种对照需已获且真实出示的材料。模型沿用requiredEvidence与本人物历史；固定途径用requiredClues和presentedClues。InvestigateMessage在合法固定警方出示时写PoliceExchangeTable，与MutationSupport.commit共用原事务；原请求回执直接返回，不重复出示。旧已完成操作和回执不补写或迁移。

IndependentChecksSuite覆盖提前访问／伪造出示、两个安排的前提、响应丢失重试、会面不自动授予、实际证言及文件库恢复、人物知识隔离、实际出示与后续记忆、固定备用及其全事务回滚。公开玩家视图不含私密资料或未获得的证言正文。


## 2026-10-29 地图、案件时钟与有限核查

新建修订8，StudyProjection继续支持3—7。CaseContent增加公开AreaMap、可选InvestigationClock与ExternalCheckSpec；加载器允许多个语义地点，但当前可玩场景仍只由spawnPlace绑定书房。地图引用实际PlaceId，校验道路两端、唯一ID、正距离及有限绘图范围。它不暴露完整truth、角色路线或新3D资产。

RequestExternalCheckMessage、AdvanceTimeMessage按既有注册规则经身份边界执行。申请时写当前GameSessionRecord.externalChecks；MutationSupport.commit按作者时钟推进并结算到期项，与状态、材料／事实、记录、成功回执同事务提交。读取不推进；重试只读原回执；时间与完成状态均以当前会话为权威，不用日志恢复。等待每次1—30分钟，前端提供5／15分钟按钮，不在JDBC事务中做现实sleep。

SessionTableInitializer以ADD COLUMN IF NOT EXISTS增加checks_json，默认空数组，保留旧表、旧进度和回执；表方法统一读取／写入该列。旧CaseContent缺externalChecks为空，clock／areaMap缺省None；旧GameSessionRecord解码补空进度。未启用时钟的旧版本不推进时间。新的check／wait前端未决请求保留原session、ID、序号和载荷，浏览器v6键及版本号不变。

核查结果没有普通调查路线，未完成不能猜route或让模型选择披露项绕过。警方新问询仅自动获得本局已完成的核查结果与受理状态，其余人物仍按实际出示；模型原始回复不是核查执行器。报告完成时进入警方点已获正文、线索和日志，完成状态不反复等待。


真实模型抽查发现医生完成报告的自然追问曾被拒。现对当前实际已知的完成报告提供无执行路线的合法披露项，仅用于引用已持有正文，不重复授予或启动检查；办理中没有这些项。警方取得本人核查结果，其他人物只有实际收到报告后才具备该项；新增回归覆盖提前引用拒绝、完成后回答、无新授予及人物隔离。
