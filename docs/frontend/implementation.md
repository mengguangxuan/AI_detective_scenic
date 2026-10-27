# 联网调查页与本机运行

现有 React／TypeScript strict、Tailwind、React Three Fiber 调查页已改为真实连接 Scala 后端。抽屉状态、可用入口与操作、线索正文和调查记录均来自后端玩家视图。前端不再预置正文或自行授予线索。

本阶段使用仅限本机的演示凭证，不是正式账户登录，也尚未接入 AI 或正式案情。书房模型沿用首版；抽屉可逆开关、观察中心切换，以及书桌／台灯／窗户／书架的公开外观观察已实现，详见 [交互状态](interaction_backlog.md)。

## 启动：两个 PowerShell 终端

都在项目根目录执行。后端只启动一份；若已有前端在运行，刷新现有页面即可。

```powershell
# 终端一：等待输出 [HTTP ready]
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 serve

# 终端二：打开 http://127.0.0.1:5173
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 dev
```

前端通过同源 /api 代理到 127.0.0.1:8080。为避免默默改端口后无法通过本机边界检查，开发服务固定5173，冲突时明确退出。Playwright 使用独立的5175，不停止用户的5173服务。

关闭前端可 Ctrl+C。也可在另一终端优雅停止本项目后端：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 stop-serve
```

后端读取本项目的停止标记后释放服务资源并退出，不按进程名批量终止 Java。出现数据库已占用时先确认是否已有服务，不删除数据库或锁文件。

## 身份与存档

后端生成随机本地凭证，以 HttpOnly、SameSite=Strict 的 /api Cookie 返回。accounts 服务保存凭证 SHA-256 摘要并解析可信 UserId；games 服务继续核对会话归属。API 不接受浏览器自报 userId，不开放案件导入／文件路径接口。HTTP 仅监听127.0.0.1，检查 Host、Origin、JSON 类型、自定义请求头、请求字段和长度，不开放 CORS。此边界用于本机课程演示，不是公网部署认证方案。

联网数据库为 backend/target/web-demo/windows/db-v1.mv.db（Linux 对应 linux）；与旧 CLI 演示数据库隔离。启动时只导入新的 browser_investigation.json，不修改旧案件修订或清空存档。四张业务表保留，另加 accounts 的 local_identities 技术表存演示凭证摘要。

浏览器只保存新的会话记录 detective-scenic:server-session:v2：会话编号、是否等待新建完成，以及未决请求的原编号、载荷和确认序号；不保存线索正文或权威现场状态。按用户明确授权，早期网页 Demo 进度已重置；前端清除旧 v1 会话定位与预览键，只使用当前存档。清除 Cookie 会丢失演示身份；页面不会因此悄悄覆盖原会话，可以明确选择新建调查。新建保留原数据库会话。

请求发送前必须先保存原载荷。响应不确定时保留原 RequestId 和 expectedSequence，包括刷新页面后重试；已确认成功但视图读取失败时只重读视图。客户端拒绝较旧视图覆盖较新状态，并检查会话与案件修订一致。服务器在会话行锁下投影一致视图，状态、调查记录和成功回执仍同事务提交。

## E 盘隔离环境

| 内容 | 实际位置 |
|---|---|
| Node.js 与随附 npm | E:\dev\nodejs |
| npm 安装包缓存 | E:\dev\npm-cache |
| 本项目进程的 npm 全局目录 | E:\dev\npm-global |
| 本项目依赖 | E:\dev\project-dependencies\detective-scenic-frontend\node_modules |
| 测试浏览器 | E:\dev\playwright-browsers |

frontend/node_modules 为指向 E 盘的 Windows 目录联接；源码和锁文件留在项目内。C 盘原安装、缓存、系统／用户 PATH、用户 .npmrc 和其他项目均保留。启动器只修改自身进程环境。

```powershell
# 首次准备；本机已完成则无需重复
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\prepare_frontend_environment.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 doctor
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 install
```

使用脚本在 E 盘实际目录安装，不对项目联接直接执行普通 npm install。TypeScript 与构建产物不应混用 Windows／WSL 的依赖。

## 课程组织与接口对应

- index.tsx 明确展示场景、检查、线索和记录；页面 types、纯函数、hooks、存储适配和组件就近组织。
- apis/accounts 与 apis/games 按服务组织。5 个公开消息对应5个前端 API 类；输入、响应解码就近，发送和错误处理在 apiClient。
- API 类的 send 使用统一命名函数，对应后端 Message 去后缀并转小写。生产构建保留类名；浏览器验收针对生产构建，避免仅开发模式命名正确。
- 同步调查仍为短事务，不在事务中等待 AI。objects 为数据和编解码，utils 为服务内纯规则，Table 封装 SQL，各表单独初始化。
- 几何、灯光和普通装饰由已登记的 study/2 场景代码提供。StudySceneView 是首个场景的受限表现投影，不宣称实现草案的通用资源／矩阵协议。

## 验收

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 test
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 test
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 build
# 后端 serve 已就绪后：
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 browser-test
```

browser-test 自行构建并启动5175上的生产预览，完成后停止自己的前端。覆盖真实调查、初始响应无正文、刷新恢复、移动布局、提交成功但响应丢失后的原请求重试、提交后刷新失败只重读视图，以及断网时不回退假数据。

它还在 frontend/.build-cache/browser-restart-state.json 保存测试浏览器身份（不提交 Git）。完成 browser-test 后，优雅停止后端、用同一数据库重新 serve，再执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 browser-restore
```

这是独立的跨进程恢复检查，复用之前测试浏览器的 Cookie 和会话编号。后端另外有 HTTP 身份、越权、伪造字段、过期序号、请求冲突、并发新建和文件恢复测试。

3D 包仍较大，构建有超过500KB分块提示；场景已按需加载。这一限制不影响调查区先加载，但尚未做移动设备性能验收。


本次验证：21项后端测试、6项前端规则／解码测试、6项生产构建浏览器测试通过；另外实际停止后端进程后，用同一数据库启动新进程并通过1项原浏览器身份恢复测试。恢复结果为序号2、两条记录、一条线索。当前只保留最新网页 Demo 配置，用户原前端进程未停止。


## 当前交互验收

1. 在“现场检查”选择抽屉，打开、阅读信件，再推回抽屉；正文仍在笔记中，信件入口消失。
2. 刷新后仍是关闭状态；再次打开可看到信件，但没有重复阅读授予按钮。
3. 在“观察环境”选择台灯、窗户、书架或书桌，或直接点击其模型；镜头中心切换，显示普通外观说明，案件操作序号不变。
4. “恢复全景”可返回初始视角，包括已经手动旋转或缩放后的情况。

repeatable 与 openedRequirement 描述程序可执行条件，completedOperations 仍只是历史“曾完成”集合。SetOpened 是实体打开状态的布尔赋值，不是枚举现实世界的一切动作。公开外观放在 CaseContent.observations，按可见节点投影；没有用普通观察结果伪造线索。

最新书房配置仍为 browser_investigation.json，修订号2；不保留并行旧版文件或旧场景兼容分支。此次清理仅针对用户明确可丢弃的网页 Demo，课程最小 CLI 夹具及其他项目不动。
