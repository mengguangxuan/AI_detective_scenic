# AI 侦探剧场

课程项目：在三维现场寻找线索，与具有独立知识和对外策略的人物交谈，出示材料、申请核查，并提交自己的推理接受评审。当前首案为《牧师寓所谋杀案》，案件编号为 `vicarage_study`。

## 作业验收入口

- [作业测试流程](docs/classroom/submission_acceptance.md)：主要功能操作、预期结果及整案验收入口。
- [整案跑通与验收流程](docs/frontend/full_case_walkthrough.md)：完整取证、会面、专业核查、评审与结案步骤。
- [手动测试指南](docs/frontend/manual_test_guide.md)：当前功能与回归检查。
- [代码讲解](docs/classroom/code_walkthrough.md)：模块职责与课堂讲解入口。
- [树林场景说明](docs/frontend/woodland_scene_manifest.md)：双场景表现、调查绑定与实际截图。

提交包包含源码、资源、依赖声明、测试和文档，不包含安装环境、编译产物、本机数据库、开发缓存或真实 API Key。演示视频由提交者另行提供。保留原有开发工具源码，本次没有新增启动脚本。

## 已实现内容

- 首页、案件区、案件详情和已有调查入口；旧修订保留原内容。
- 书房与树林双场景调查：物品检查、开关、盆栽检查、树林定点步行、拨开落叶和实地采样。
- 人物档案、自由问询、材料出示、连续接话、必要的澄清与固定问答备用；实际答复由独立语义步骤逐项登记。
- 线索整理、调查日志、警方交流、会面安排和具有办理时间的专业核查。
- 推理提交、反馈与修改、明确结案和冻结复盘；提前放弃查看解答单独记录。
- 后端权威存档、原请求重试、事务一致性和刷新／重启恢复。

## 源码结构

| 目录 | 内容 |
| --- | --- |
| `backend/src/main/scala/services/` | 按账户、案件、调查、模型与系统职责组织的后端 |
| `backend/src/test/scala/` | 规则、权限、事务、HTTP 和恢复测试 |
| `frontend/src/` | 页面、调查状态、API 与三维场景 |
| `frontend/public/` | 静态资源 |
| `frontend/tests/` | 浏览器回归与重启恢复检查 |
| `docs/fixtures/` | 当前及历史案件配置，运行和兼容测试所需 |
| `docs/` | 操作流程、案件依据、实现记录与课堂说明 |
| `tools/` | 原有开发与验收工具源码 |

## 环境与模型

后端采用 Scala、Cats Effect、Circe、嵌入式 H2；版本见 `backend/build.sbt` 和 `backend/project/build.properties`。前端采用 React、TypeScript、Vite、Tailwind、Three.js 和 React Three Fiber；依赖见 `frontend/package.json` 与 `frontend/package-lock.json`。

项目已在 Windows、JDK 21 和现有 Node 环境下验证。既有 Windows 开发脚本含本机 E 盘工具路径，不能作为任意电脑通用的一键启动器。配置与原开发操作见[验收准备](docs/frontend/acceptance_preparation.md)；本次按源码交付，不新增启动脚本。

模型由后端调用。保留空密钥模板 `backend/config/model-api.example.json`，实际配置 `backend/config/model-api.local.json` 不随包提交，接收者可自行配置。没有可用模型时固定问答仍可取证，自由问询和模型推理评审需要另行配置后验收。

## 当前范围

树林采用三处定点步行，其他地图地点主要提供示意与路程估算。案件上传、运行时生成新案件、完整账号体系和单人嫌疑人玩法尚未实现。模型正文仍可能出现事实补造或接话偏差，格式与权限检查不能代替语义验收，见[交互待办](docs/frontend/interaction_backlog.md)。

`plan.md` 是初期规划，不代表全部完成。历史文档按日期和修订阅读，当前操作以最新指南为准。案件作者配置及案件文档包含剧透。开发约定见 [AGENTS.md](AGENTS.md)，其中引用的外部课程规则不属于运行依赖。
