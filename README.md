# AI 侦探剧场

课程项目，已完成领域类型初稿与可运行的 Scala 最小调查切片。

- [项目约定](AGENTS.md)
- [功能规划（实现方式仅参考）](plan.md)
- [领域类型 v1.4](docs/backend/case_truth_types.md)
- [Scala 最小流程、运行方式与测试](docs/backend/minimal_backend.md)
- [最小示例及验证边界](docs/backend/minimal_fixture.md)
- [API／消息草案（独立待议）](docs/backend/api_design_draft.md)
- [第二轮类型评审与复核](docs/backend/type_review_round2.md)

从本目录运行文档契约检查：

```bash
python3 tools/validate_minimal_fixture.py
```

该 Python 命令只检查文档契约。运行 Scala 切片：

```bash
python3 tools/backend_sbt.py test
python3 tools/backend_sbt.py run
```

Windows PowerShell 不需要 Python，直接执行：

```powershell
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 test
powershell -NoProfile -ExecutionPolicy Bypass -File .\tools\backend.ps1 run
```

Scala 切片使用嵌入式 H2 验证调查、事务、去重和重放；HTTP、登录、3D 场景及 AI 尚未实现。

远端仓库：<https://github.com/mengguangxuan/AI_detective_scenic>

课程规则位于工作区外部相对路径 `../samples/samples/rules.txt`，不是本仓库内文件；独立克隆时需要另外提供课程规范。
