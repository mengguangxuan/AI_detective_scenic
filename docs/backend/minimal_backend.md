# Scala 最小调查流程

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
2. test 最后包含 `Passed: Total 12, Failed 0, Errors 0, Passed 12` 与 `[OK] test completed`。
3. run 显示：提前阅读为 `Left(当前无法访问该调查入口。)`；获得“信上写着：今晚九点见。”；相同请求重试为 `true`；恢复后的 sequence 为 2，含 clue_letter 与 fact_letter_text，最后出现 `[验收通过] 调查、请求去重、关闭数据库后恢复均成功。`。

提前阅读的 Left 是预期的权限拒绝，不是演示失败。当前无网页可打开，验收对象是这条 Scala／数据库流程，不是视觉场景。

Windows 日志保存在 backend/.build-cache/windows-test.log 与 windows-run.log。卡住或失败时保留完整日志；首次下载时终端会先显示环境与日志位置，随后才是 sbt 输出，不应把尚未显示测试结果视为通过。

### 本次 FileSystemException 的原因与修复

实际检查到 backend/target/out 下的 inc_compile_3.zip 是指向 WSL `/tmp/detective-sbt/...` 的符号链接。Windows 无法通过该链接读取编译缓存；此前在 WSL 跑通并不能证明同一输出目录可以直接给 Windows 复用。

启动器现在记录输出平台；发现未知输出或切换 Windows／Linux 时，将 target/out、target/streams、project/target 移到 backend/.build-cache/output-backups/，不删除源码或 target/fixture-demo 中的数据库，然后重新生成当前平台输出。各平台的归档分开保留；不支持两边同时构建。直接绕过入口在 IDE 里构建前，也应确保当前缓存属于该平台。

已在 Windows 原生 PowerShell 实际完成诊断、旧缓存归档、重新编译、全部 12 项测试和演示。启动 JVM 与 fork 出的演示／测试 JVM 均显式使用 UTF-8，中文信件及错误提示已实际显示正确。

当前使用本机已有的 sbt 2.0.9、Scala 3.9.0 及 Java 21；Cats Effect 与 Circe 版本沿用课程样本。H2 是本阶段嵌入式数据库选择，MUnit 用于自动测试；不是把课程后端改为 Python。

演示先拒绝提前阅读信件，随后打开抽屉、阅读固定内容、以相同请求编号重试，最后关闭连接重新恢复会话。输出显示线索内容、序号及恢复状态。数据库使用一次解析后的绝对路径，保存在 backend/target/fixture-demo/windows/db.mv.db（Windows）或 linux/db.mv.db（Linux）。每次演示创建新会话，关闭前后比较完整状态；恢复连接使用 IFEXISTS=TRUE，不允许默默创建新数据库。旧的 backend/target/fixture-demo/db.mv.db 保留，不自动迁移或删除。

## 课程规范的落实

- 全部生产 Scala 代码在 src/main/scala/services/ 下；cases 管最小案件输入，games 管会话／调查，system 管消息基类、通用值及 JDBC 事务和应用入口。
- 每个数据类型独立文件，使用 final case class；编解码在同名伴生对象；ID 是独立类型，含 Map 键编解码。开放事实内容保留 Text。
- 纯规则在 games/utils，夹具解析规则在 cases/utils，均使用 private[所属服务] 可见性；不跨服务调用私有 utils。
- 每个操作独立 Message，构造参数承载输入，入口为 plan(connection: Connection): IO[Response]；响应放 games/objects/apiTypes。没有转发用 Service 层。
- 每张表有独立目录与 TableInitializer；SQL 私有并与公开 IO 方法相邻。消息不拼 SQL。SessionTable 私有查询辅助方法不对外接受任意 SQL。
- 消息在外层 DatabaseSession.transaction 中执行。写入先锁会话、校验 owner、检查相同请求回执，再校验预期序号、重放、纯规划，最后同事务写入日志、会话序号和回执。无 AI／网络等待混入事务。

system/api 中的 DatabaseSession 和 FixtureDemo 是共享事务与应用启动边界，符合课程对基础设施归属的要求；不是领域对象或业务 Service。LoadFixtureMessage 是制作期内部读文件入口，不接受浏览器提交路径。

## 持久化与恢复

三张表分别保存会话、追加日志、成功请求回执。会话保存被冻结的类型化夹具投影、初始状态和当前序号；不依赖重新读取可修改的 JSON 文件恢复旧会话。日志存自足的检查与获知记录，查询时从序号0状态重放；序号及 reducerVersion 不匹配即拒绝。

本切片没有长时间操作，因此不持久化 Pending：会话行锁串行化短事务，同一事务保存成功回执；失败完整回滚。若以后接入 AI，必须另行落实短事务登记 Pending／释放连接／调用模型／短事务复核，不得直接沿用此同步事务模型。

同 RequestId 同载荷返回原响应，即使 expectedSequence 已经过期；同 ID 不同载荷拒绝；新 ID 携带旧序号拒绝。不同新请求对同序号竞争只允许一个提交。只记录 Investigated 不授予知识；Acquired 才更新事实和线索。

## 范围与限制

这是完整 CasePackage 的**最小调查投影** InvestigationFixture，不宣称已实现整个领域初稿。Fixture* 类型是阶段实现，不替换文档中的通用案件类型。加载器明确拒绝非 InvestigationOnly、未来事件、人物、评分、非 Fixed 路线、不支持条件／效果和带旋转／缩放的节点；当前只有一间房，节点支持父链平移与半径距离检查。不支持跨地点移动或通用矩阵变换。

它校验所消费的运行字段和引用，不是完整案件发布校验器。文档 Python 校验仍检查原始完整 JSON 的形状。占位模型没有被渲染，视觉验收没有完成。

消息目前仅供本地可信程序调用，未注册 HTTP routes，也没有前端或登录服务；userId 由本地调用方提供，消息仍验证会话归属。未来开放 HTTP 必须先按课程规范由 accounts 校验令牌，再构造可信 userId；不能把当前内部消息直接公开为“已鉴权 API”。

## 验证结果

12 项集成／规则测试通过：隐藏父链访问、固定线索获知、请求去重、载荷冲突、过期序号、跨用户拒绝、后段失败完整回滚、双连接竞争、相同请求并发重试、日志版本／顺序及编解码、文件数据库关闭后恢复、不支持协议拒绝。部分相关断言归在同一测试中。数据库结果仅针对 H2；未声称验证 PostgreSQL 或真实进程崩溃／断电。

## 恢复失败不是正常验收结果

用户曾报告：前两步成功，但关闭连接后报“无法访问该会话”并以退出码1结束。这是失败；不能将提前阅读的预期权限拒绝与最终恢复失败混淆。复查时未复现该单次失败，原数据库中也未找到该会话，不能据此宣称已确定或彻底修复其原因。用户确认只运行了一个 PowerShell，暂无依据将其归因于并发构建。

随后加固：打印固定的数据库绝对路径、按操作系统区分演示文件、恢复时 IFEXISTS=TRUE、比较关闭前后完整状态、明确打印成功／失败标记。新增回归测试在同一个文件数据库连续创建20个会话，每次关闭连接，重新打开后核对所有既有会话。此测试在原生 Windows 通过，但加固与通过测试不等于已解释此前单次失败。

若更新后再出现失败，保留完整 windows-run.log；其中应包含数据库路径及待恢复会话编号。出现 `[FAILED]` 或非零退出码均不得判为通过。
