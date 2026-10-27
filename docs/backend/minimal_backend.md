# Scala 最小调查流程

已实现一个本地 Scala／JDBC 调查切片。输入沿用 `docs/fixtures/minimal_investigation.json`；本次不新增正式案情、3D 网页或 AI 审讯。

## 启动与测试

在项目根目录运行（需要 Java，复用已有 sbt 或 IntelliJ Scala 插件中的启动器）：

```bash
python3 tools/backend_sbt.py test
python3 tools/backend_sbt.py run
```

工具不下载启动器；首次运行 sbt 会下载尚未缓存的项目依赖。可以用 SBT_LAUNCH_JAR 指定本地启动器，用 DETECTIVE_BUILD_CACHE 指定可写缓存目录，默认系统临时目录下的 detective-sbt。没有命令行 Python 时，也可在 IntelliJ 中打开 backend 作为 sbt 项目，通过 sbt 面板执行 test／run；若终端已装 sbt，则在 backend 执行 `sbt test`／`sbt run`。

当前使用本机已有的 sbt 2.0.9、Scala 3.9.0 及 Java 21；Cats Effect 与 Circe 版本沿用课程样本。H2 是本阶段嵌入式数据库选择，MUnit 用于自动测试；不是把课程后端改为 Python。

演示先拒绝提前阅读信件，随后打开抽屉、阅读固定内容、以相同请求编号重试，最后关闭连接重新恢复会话。输出显示线索内容、序号及恢复状态。数据库保存在 backend/target/fixture-demo/，每次演示创建新会话，不覆盖已有会话。

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

11 项集成／规则测试通过：隐藏父链访问、固定线索获知、请求去重、载荷冲突、过期序号、跨用户拒绝、后段失败完整回滚、双连接竞争、相同请求并发重试、日志版本／顺序及编解码、文件数据库关闭后恢复、不支持协议拒绝。部分相关断言归在同一测试中。数据库结果仅针对 H2；未声称验证 PostgreSQL 或真实进程崩溃／断电。
