# 调查页首版与本机运行

首版是显式的本地交互预览：React + TypeScript strict、Tailwind、React Three Fiber。书房使用本地基础几何体，无外部模型或图片依赖。包含对象选择、打开抽屉、阅读信件、线索面板、调查记录、本地进度恢复和重置；小屏按单列显示。WebGL 失败时保留文字调查入口。

本版尚未连接 Scala 后端，不是鉴权／数据库／AI 版本。预览信件是公开测试数据，包含在客户端；正式接入时必须移除预览结果来源，从后端获准投影读取。浏览器 localStorage 仅保存预览状态，不能当作服务端存档。对象选择与场景动画不授予正式线索。

## E 盘隔离环境

| 内容 | 实际位置 |
|---|---|
| Node.js 与随附 npm | E:\dev\nodejs |
| npm 安装包缓存 | E:\dev\npm-cache |
| 本项目进程的 npm 全局目录 | E:\dev\npm-global |
| 本项目依赖 | E:\dev\project-dependencies\detective-scenic-frontend\node_modules |
| 浏览器测试资源 | E:\dev\playwright-browsers |

项目 frontend/node_modules 为 Windows 目录联接，实际内容在 E 盘。package.json、锁文件和源码仍放在项目内供 Git 管理。hw_mooddiary 没有已安装的 node_modules，复用的是其版本信息及本机已有 npm 缓存。3D 等缺少的包另行安装。

复制已有 Node/npm 与缓存后核对 node.exe 的 SHA-256。保留 C 盘原安装和缓存，不修改系统／用户 PATH、注册表、用户 .npmrc 或其他项目。启动器只在自己的子进程中配置 PATH、缓存及 prefix。此过程是隔离副本并切换本项目入口，不是卸载 C 盘的系统安装。

## PowerShell 命令

在项目根目录运行：

```powershell
# 首次准备或换电脑时；本机已有 E 盘环境则不必重复
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\prepare_frontend_environment.ps1
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 doctor
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 install
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 dev
```

打开终端输出的本地地址（默认 http://127.0.0.1:5173）。Ctrl+C 停止。不要同时启动多个同端口服务。

请使用项目脚本安装依赖。脚本在 E 盘实际目录执行 npm install，再同步锁文件与建立目录联接，避免 npm 对目录联接的重建行为把依赖写回 D 盘。

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 build
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 test
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 browser-install
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\frontend.ps1 browser-test
```

浏览器测试需要 E 盘已安装对应 Playwright 浏览器。build 执行 TypeScript 检查与生产构建；test 验证状态转换和存档校验；browser-test 实际打开网页检查操作、刷新恢复、小屏与控制台。生成文件不提交 Git。

## 课程代码组织

- pages/Investigation/index.tsx 直接展示场景、检查、线索和记录等业务区域。
- objects 定义本页类型，functions 存纯规则，utils 隔离本地存储副作用，hooks 协调页面行为，components 按可见区域组织。
- 预览不建立虚假的 apis/games 文件。真实 HTTP 消息注册后，再按独立 Message 对应前端 API 文件，并把发送、身份处理和解码基础设施放在共享位置。
- 页面样式只用 Tailwind；styles.css 只有 Tailwind 导入。Three.js 材质用于场景绘制。
- 状态转换为纯函数；localStorage、确认框与渲染副作用在边界调用。存档经过运行时校验，不能用类型断言替代检查。

## 手工验收

1. 初始显示“本地预览”，没有信件正文或信件入口。
2. 选“书桌抽屉”，点“打开抽屉”；信件出现，可从 3D 标记或文字入口选择。
3. 点“阅读信件”，线索笔记显示正文，调查记录出现两条。
4. 刷新浏览器，抽屉、线索和记录保持；重置须确认。
5. doctor 显示 E 盘可执行文件、缓存、prefix 与联接目标；其他终端的原 Node 环境不变。

## 本次验证结果

原生 Windows 的 E 盘 Node 环境下，TypeScript strict 检查与生产构建通过；3 项纯规则／存档校验测试通过；2 项 Playwright 浏览器测试通过，覆盖调查闭环、刷新恢复、WebGL 场景显示、390px 小屏无横向溢出、损坏进度提示及确认重置。测试监听到的未处理页面异常为零。截图位于 frontend/test-results（不提交）。

Three.js 渲染包较大，已按需加载场景，文字调查区可以先显示；生产构建仍会提示 3D 分块超过500KB，这是当前首版的体积限制。未声称验证 HTTP、身份隔离、后端存档或完整案件。
