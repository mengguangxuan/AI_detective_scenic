# AI 侦探剧场

课程项目，当前处于领域类型与最小调查切片准备阶段。

- [项目约定](AGENTS.md)
- [功能规划（实现方式仅参考）](plan.md)
- [领域类型 v1.4](docs/backend/case_truth_types.md)
- [最小示例及验证边界](docs/backend/minimal_fixture.md)
- [API／消息草案（独立待议）](docs/backend/api_design_draft.md)
- [第二轮类型评审与复核](docs/backend/type_review_round2.md)

从本目录运行文档契约检查：

```bash
python3 tools/validate_minimal_fixture.py
```

此检查不代表 Scala 后端、3D 场景或数据库已实现。

远端仓库：<https://github.com/mengguangxuan/AI_detective_scenic>

课程规则位于工作区外部相对路径 `../samples/samples/rules.txt`，不是本仓库内文件；独立克隆时需要另外提供课程规范。
