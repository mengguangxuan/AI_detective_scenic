# 人物资料与交互配置

适用于新建案件修订10。本次调整的是人物制作与资料读取方式，未扩展剧情。原修订3—9和未决问询保留原行为。

## 1. 三部分分别负责什么

| 部分 | 位置 | 内容 |
|---|---|---|
| 固定人物资料 | 作者文件根节点`characters` | 身份、关系、背景、说话风格，以及本人知识／经历、私人内情、初始对外策略 |
| 固定交互配置 | `investigation.interviews`、`police`，以及原有`routes`／`followUps`等 | 接触入口、可披露材料、实际出示前提、阶段转换和阶段策略调整 |
| 本局状态 | 数据库会话及问询／行动记录 | 实际出示的材料、已发生的对话、已完成阶段；不修改作者人物资料 |

人物资料通过PersonId标识。交互定义使用WitnessId，并以person字段引用PersonId；人物本身不反向引用问询ID、入口或路线。死者和暂不可问询的人也使用同样的人物资料结构，只是不配置问询绑定。

初始策略属于人物资料；改变策略的前提和调整内容留在followUps；本局当前阶段由completedOperations等现有状态决定。私人内情不因对外改口而自动变化。

## 2. 填写示例

以下是完整作者案件文件中的人物／交互片段，需与已有地点、节点、入口、路线和线索配置配合，并非可独立导入的完整案件。

```json
{
  "characters": [
    {
      "id": "porter",
      "name": "陈先生",
      "identity": "门房",
      "victimRelation": "负责死者住所的出入登记",
      "caseRelation": "当天值班，可以说明亲眼见到的来访情况",
      "background": "在这里工作多年，熟悉住户和常客。",
      "voice": "说话简短，记不清时会明确说记不清。",
      "dossier": {
        "knowledge": [
          "本人经历：下午一直在门房值班，六点左右见过送货员。",
          "本人判断：当时以为送货员和往常一样来送货，并不知道包裹内容。"
        ],
        "privateContext": ["本人隐瞒：曾短暂离岗，不想让雇主知道。"],
        "policy": ["愿意说明亲眼所见，但初次询问时回避离岗的事。"]
      }
    }
  ],
  "investigation": {
    "interviews": [
      {
        "id": "interview_porter",
        "person": "porter",
        "point": "point_porter",
        "answers": [
          {
            "id": "porter_delivery",
            "text": "六点左右有人来送货，我看见他进了门。",
            "routes": ["route_porter_delivery"],
            "requiredEvidence": []
          }
        ]
      }
    ],
    "police": null
  }
}
```

知识文字可以按本人经历、听闻、信念／误解等明确分段；当前不强制把开放语义拆成可执行枚举。未知信息不要凭空补齐；模型只收到所选人物的资料、合法材料和实际对话历史。

### 人物字段

- `id`：稳定人物ID，对应PersonId。
- `name`、`identity`、`victimRelation`、`caseRelation`：用于公开人物简介，不能填写未公开秘密。
- `background`：供人物扮演使用的背景，并会出现在该人物已开放的问询简介中，不应混入秘密。
- `voice`：表达风格。
- `dossier.knowledge`：本人经历、已知人物、听闻及主观判断；每条写清来源和范围。
- `dossier.privateContext`：本人知道但不愿公开的内情，不是整案全知资料。
- `dossier.policy`：初始对外策略，允许隐瞒或撒谎，但不能改变历史。

每组资料最多30条，每条最多1200字。可问询人物必须有knowledge，其他人物可留空数组。当前字段严格解析，`characters`中混入point／answers等交互字段会被拒绝。

### 问询字段

- `id`：稳定问询ID，对应WitnessId，可以与人物ID采用不同值。
- `person`：已存在的人物ID。同一人物当前只允许一个问询身份。
- `point`：已经登记的调查入口；实际可接触性仍由节点和阶段条件控制。
- `answers`：模型可申报的披露材料及固定问答的关联。routes指向已有路线，requiredEvidence要求本人实际收到相应材料；拥有材料不等于已经出示。

不要在interviews重复填写姓名、背景或知识。不得同时维护新characters与旧publicInfo.background.persons／investigation.witnesses列表，避免两个来源不一致。

警方也在characters和interviews填写同样的结构，在`investigation.police`写`{"witness":"interview_inspector"}`指定其问询ID。该人物得到本局已完成警方核查和实际登记材料的权限；仅姓名含“警察”不会获得权限。警方同样填写合法answers，并接受相同的路线归属与披露条件校验。

## 3. 从作者配置到运行时

```text
CharacterProfile + WitnessInteraction
  → CharacterConfiguration解码、校验并关联
  → 固定案件修订中的运行投影
      · 公开人物简介
      · 编译后的WitnessProfile
      · police保存指向列表中问询对象的WitnessId
  → GetDialogueKnowledgeMessage按案件ID、修订、问询ID读取本人dossier
  → InterviewRules按当前阶段、实际出示材料和历史组合上下文
  → 模型回复经过合法披露校验后提交
```

WitnessProfile是编译后的运行／冻结上下文结构，出现入口与材料是为了执行问询，不是要求作者把交互规则塞回人物资料。其内部复用DialogueDossier，旧扁平JSON中的knowledge、privateContext、policy仍可解码和编码。运行投影按现有接口需要保留字段，避免前端改协议；作者只维护一份人物资料。

导入时资料随案件修订保存。修改源文件不会悄悄改变已发布修订；同一修订重复导入必须语义一致，否则拒绝覆盖。没有为每个人物新增一套数据库表或CRUD接口。

## 4. 旧调查兼容和范围

- 旧修订3—9继续读原案件内容；其原本外置的对话资料由LegacyDialogueKnowledge兼容读取，vicarage_dialogue.json保留为历史兼容资源，新案件不依赖它。
- 旧独立警方对象在LegacyPoliceProfile解码兼容处归入统一WitnessProfile列表，缺ID的旧格式恢复原melchett身份，缺披露配置的旧格式按原路线生成能力。此转换只读旧数据，不回写旧修订。运行时已删除PoliceContact类型，警方只保留WitnessId引用。
- 新作者格式不变，运行快照的新写入格式为统一witnesses列表加police问询ID；旧对象格式仍可读取。人物列表维持普通对象顺序，警方放在最后，使既有修订10规范化后与同修订源文件导入值相等。
- 已冻结问询上下文和responseVersion保留；旧未决请求、原回执、场景4、v6浏览器键不重写。
- 统一的是人物制作与后端资料读取。网页仍使用已登记的study/4场景和本案入口；没有因此实现任意新案件的场景生成、案件大厅或全案发布平台。

实际配置见[当前案件作者文件](../fixtures/browser_investigation.json)，整理计划见[cases整理方案](../cases_cleanup_plan.md)。
