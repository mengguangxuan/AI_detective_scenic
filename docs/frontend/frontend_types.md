# 前端类型初稿 v0.2

本文为待讨论的前端契约，不代表已创建前端源码、HTTP 接口或 3D 场景。优先实现现有书房夹具的浏览器闭环，不重新做全量类型评审。

依据：[项目约定](../../AGENTS.md)、[功能规划](../../plan.md)、[课程规范](../../../samples/samples/rules.txt)、[后端领域类型](../backend/case_truth_types.md)、[API 草案](../backend/api_design_draft.md)、[已实现的表与类型](../backend/table_types.md)。前端使用 React／TypeScript，3D 拟采用 Three.js／React Three Fiber；实现时再确定兼容版本。

## 1. 其他页面暂缓到什么程度

可以暂缓页面专用类型，不能把它解释成取消必备页面。每项能力在开始实现前补自己的最小契约，不为了将来页面而先扩充一大组空类型。

| 页面／能力 | 当前处理 |
|---|---|
| 3D 调查场景、操作结果、线索与调查记录 | 本文先定可实现的初稿 |
| 身份检查、登录失效、加载／请求失败 | 只保留影响调查页的边界状态；真实鉴权仍须后端实现 |
| 登录／注册表单、账户资料 | 暂不定义具体字段、令牌存储或密码类型 |
| 案件列表／简介／选择、进度列表 | 暂缓页面类型；将来复用公开摘要，不读取 CasePackage |
| 独立调查日志／人物档案页 | 已获线索与记录可以复用本文视图，完整页面状态随后设计 |
| NPC 审讯、指控、评分复盘 | 保留原功能目标；开始该条切片前再拟接口与页面类型 |
| 生成、嫌疑人挑战及统计等其他规划页面 | 不在本轮决定删减或提前实现 |

## 2. 三种数据的边界

| 数据 | 负责什么 | 真值来源 |
|---|---|---|
| 后端玩家视图 | 此刻可见节点、可调查入口、获准操作、已获线索与公开记录 | 后端投影 |
| 前端页面状态 | 正在加载、选中对象、请求等待、视图刷新、网络结果不确定 | 前端状态机 |
| 制作期表现清单 | 代码／模型提供哪些节点、点击区域和姿态，镜头怎样摆放 | 制作代码与人工／AI 验收 |

不把制作期 CasePackage、后端 CaseRevisionRecord／CaseContent、GameSessionRecord、SessionRecord、RequestReceiptRecord 或人物私密视角直接复制成前端数据仓库。后端物体是否打开是一条业务状态，动画进度是前端表现状态，两者不共用一个可随意修改的对象。

theNPC 的语义世界与人物状态分离、CyberQuest 的画面与热点分离，已记录在 AGENTS.md；这里将热点换成 3D 点击区域，调查结果仍交后端裁定。AI 在制作阶段根据语义与 SceneRequirement 写场景代码；运行时只加载已登记代码／资产，不执行服务端任意生成的程序文本。

### 与四张后端表的对应

前端围绕玩家任务组织视图，不为每张数据库表再建一份前端 Record。

| 已实现的存储 | 面向前端的用途 | 仍需补充的部分 |
|---|---|---|
| case_revisions / CaseRevisionRecord | 提供当前会话所绑定修订的公开名称、场景配置、获准结果文本 | 过滤秘密内容，关联已审核场景包；不下发整个 CaseContent |
| game_sessions / GameSessionRecord | 判断当前可见入口、节点姿态、可执行操作和累计知识 | 生成 SceneView；状态是当前值，不要求前端重放日志 |
| session_records / SessionRecord | 提供玩家本次调查与操作的公开经过 | 投影 InvestigationLogEntry；只显示本会话获准记录 |
| game_receipts / RequestReceiptRecord | 服务端判断请求是否重复并返回原响应 | 前端只收 CommandResult，不读取回执表、digest 或存储版本 |

前端的 InvestigationSnapshot 是一次查询产生的组合视图，不是新增数据库表，也不叫存档快照。固定内容来自会话绑定的案件修订，当前场景和累计知识来自当前会话，日志来自该会话记录。后端需要保证这几部分对应同一 sequence；普通事务在默认隔离级别下分多次查询并不自动保证这一点。实现时可在短事务内锁定会话行后读取并投影，或采用经验证的一致读取方案。

ActionPlan 是后端纯规则的临时结果，不参与浏览器协议。前端纯函数负责解码后映射、状态归并和选择校验，不重新计算线索授予或业务效果。

## 3. 基础值和编号

以下 TypeScript 代码块合起来是一份契约。集中展示仅方便阅读，实现时按有意义的类型分文件，通用值与调查页专用类型分别归属。

```typescript
// 品牌仅防止把不同编号混用。真实数据仍须运行时解码，不能用 as 冒充验证。
declare const idBrand: unique symbol;
type Id<Name extends string> = string & { readonly [idBrand]: Name };

type CaseId = Id<"CaseId">;
type SessionId = Id<"SessionId">;
type RequestId = Id<"RequestId">;
type PlaceId = Id<"PlaceId">;
type SceneNodeId = Id<"SceneNodeId">;
type AssetId = Id<"AssetId">;
type PointId = Id<"PointId">;
type RouteId = Id<"RouteId">;
type OperationId = Id<"OperationId">;
type ClueId = Id<"ClueId">;
type RecordId = Id<"RecordId">;

// 制作资源的稳定编号，不把世界里的物品类别或动作变成枚举。
type SceneBundleId = Id<"SceneBundleId">;
type VisualPresetId = Id<"VisualPresetId">;

type CaseRevision = number;
type Sequence = number;
type SceneBundleRevision = string;
type Vec3 = Readonly<{ x: number; y: number; z: number }>;

type Matrix4 = readonly [
  number, number, number, number,
  number, number, number, number,
  number, number, number, number,
  number, number, number, number
];

type SnapshotStamp = Readonly<{
  sessionId: SessionId;
  caseId: CaseId;
  caseRevision: CaseRevision;
  sequence: Sequence;
}>;
```

约束：ID 非空、不静默归一化；CaseRevision 为正整数；Sequence 为非负安全整数。Scala Long 与 JS number 的精度并不相同，面向浏览器的协议必须限制到 Number.MAX_SAFE_INTEGER，越界拒绝或显式改版使用十进制字符串，不能仅在 TypeScript 中写 number 就当作已兼容。

Vec3 使用所属 Place 坐标、米、右手系、Y 向上；Matrix4 为16个有限数值、列主序，表示节点模型局部坐标到该 Place 的变换。矩阵长度与数值有限性需解码校验。

## 4. 后端返回的场景视图

```typescript
type SceneModelAsset = Readonly<{
  id: AssetId;
  url: string;
}>;

type SceneNodeView = Readonly<{
  id: SceneNodeId;
  assetId: AssetId;
  localToPlace: Matrix4;
  visualPresetId: VisualPresetId;
}>;

type FixedInvestigationChoice = Readonly<{
  routeId: RouteId;
  label: string;
}>;

type OperationChoice = Readonly<{
  operationId: OperationId;
  label: string;
}>;

type InquiryPointView = Readonly<{
  id: PointId;
  label: string;
  positionInPlace: Vec3;
  radiusMeters: number;
  fixedChoices: readonly FixedInvestigationChoice[];
  operations: readonly OperationChoice[];
}>;

type SceneView = Readonly<{
  placeId: PlaceId;
  placeName: string;
  sceneBundleId: SceneBundleId;
  sceneBundleRevision: SceneBundleRevision;
  models: readonly SceneModelAsset[];
  nodes: readonly SceneNodeView[];
  points: readonly InquiryPointView[];
  playerPosition: Vec3;
}>;
```

具体语义：

- models 仅用于登记允许加载的模型资源；首个书房也可使用已审核的本地基础几何体。空 models 合法，本地代码依据已登记 AssetId 构建几何；未登记的资源 ID 或 URL 必须拒绝，不直接导入任意脚本。
- nodes 只包含此刻允许显示的业务节点，所有 assetId 在当前表现包／获准模型清单中可解析。装饰可以由制作代码实现，不必给每根桌腿创建案件 Entity。
- points 只包含后端允许访问的空间调查入口；PointId 与模型节点独立，不假定一个模型只能有一个调查入口。没有对应获准 PointId 的点击区域不能发送调查。首版仅固定操作与固定检查，不假装已经接入自由语义调查。
- radiusMeters > 0 表示后端检查可达性的业务半径，不等于可见的小圆点或点击区域尺寸。画面可先选中入口，但是否可执行仍由后端当前状态校验。
- playerPosition 为后端接受的位置；首版固定调查位置和观察镜头，不实现自由走动。镜头旋转／拉近不自动变成人物位移。多地点出口与移动协议在实际做导航前补，不在这里锁死成只有一间房的产品限制。
- localToPlace 决定节点锚点与布局；visualPresetId 决定该节点内部的获准表现，例如抽屉关闭／拉开。姿态是制作包内可扩展编号，不是 OpenDrawer、FlipFrame 等现实动作的全局枚举。前端只能选择该节点已登记的姿态。

**这一版需要新增后端投影，不是当前接口已能返回的数据。** 当前 CaseContent 存节点平移，GameSessionRecord 存 opened 与 visibleNodes 等当前状态；最小适配可生成平移矩阵，并按已登记的“实体打开状态 → 姿态编号”绑定投影抽屉。绑定由作者提供并审核，不通过解析 label 或猜测动作文字决定姿态。刷新后同样从后端状态恢复目标姿态，不能靠上一次动画是否播放过恢复。

此前 API 草案只定义了矩阵，没有 visualPresetId。本字段是本轮建议：在通用领域层尚未确定绑定结构前，先在单个夹具的受限适配中落实；不是暗中把新能力视为已实现。

## 5. 已获得的线索、公开日志与一致快照

```typescript
type ClueView = Readonly<{
  id: ClueId;
  title: string;
  text: string;
}>;

type InvestigationLogEntry = Readonly<{
  id: RecordId;
  sequence: Sequence;
  storyTimeLabel: string;
  account: string;
  clueIds: readonly ClueId[];
}>;

type InvestigationSnapshot = Readonly<{
  stamp: SnapshotStamp;
  scene: SceneView;
  clues: readonly ClueView[];
  journal: readonly InvestigationLogEntry[];
}>;
```

首版线索只显示审核过的文本，图片／音频在确实需要前再扩展媒体协议，不先建复杂媒体系统。线索可以来自缺失、报告或谈话，ClueView 不要求模型节点、坐标或实体。

journal 是 SessionRecord 的玩家公开投影，包含现场操作与调查，不是原始存储行。id、sequence、disclosedClues 分别映射到 id、sequence、clueIds；text 经公开内容审查后映射到 account；storyTimeSeconds 结合案件 timeOriginLabel 格式化为 storyTimeLabel。当前操作不推进剧情时间，因此多条记录可以具有相同时间标签。不得下发 NPC 内心、规则前提、隐藏事实或完整获知来源。storyTimeLabel 是后端提供的展示文字，不用于前端重新判断事件先后；排序使用 sequence，同序号保留服务端数组顺序，RecordId 在当前会话内唯一；跨会话缓存记录时使用 (SessionId, RecordId) 组合键。

整个 InvestigationSnapshot 必须对应同一会话、案件修订与 sequence。固定案件修订绑定场景包版本，同一 stamp 不能悄悄换成另一版本的场景或不同事实内容。场景、线索和日志必须在一次一致读取中产生；不能把序号2的场景与序号1的日志拼成“当前状态”。空 clues／journal 合法，不等于页面没有加载完成。

前端不得预存“今晚九点见”再等阅读后显示。秘密正文不写入初始 JS、贴图、完整 GLB 或隐藏 DOM；最初只提供一般纸张模型。即使有人修改前端可见性，也不能因此从本来未获准的响应中找到秘密。

## 6. 操作输入与回执：与 API 设计保持分工

```typescript
type MutationMeta = Readonly<{
  sessionId: SessionId;
  requestId: RequestId;
  expectedSequence: Sequence;
}>;

type ExecuteOperationInput = MutationMeta & Readonly<{
  operationId: OperationId;
}>;

type InvestigateInput = MutationMeta & Readonly<{
  pointId: PointId;
  routeId: RouteId;
}>;

type MutationAcknowledgement = Readonly<{
  sessionId: SessionId;
  requestId: RequestId;
  resultSequence: Sequence;
  messages: readonly string[];
  acquiredClueIds: readonly ClueId[];
}>;

type PendingMutation =
  | Readonly<{ kind: "execute"; input: ExecuteOperationInput }>
  | Readonly<{ kind: "investigate"; input: InvestigateInput }>;
```

两种 PendingMutation 是当前页面真正支持的有限请求协议，不是穷举现实调查行为，也不对应一个聚合所有动作的通用 HTTP API。独立操作仍应分别对应课程的独立 Message 和前端 API 文件。

这些是前端逻辑输入与待议回执，不是宣布已有 HTTP 消息形状。MutationAcknowledgement 是页面适配结果，不要求后端增加同名包装：sessionId、requestId 来自本次 pending，resultSequence、messages、acquiredClueIds 分别来自 CommandResult.sequence、findings、acquiredClues。响应必须绑定到对应请求，不能从当前选中对象猜来源。请求不得携带客户端自报的可信 userId、任意状态补丁、线索结果、模型代码或效果数组。令牌处理在共享发送边界完成，后端路由再验证并生成可信身份；页面不复制一套账户规则。

首版采用“操作回执 → 重新读取一致快照”：回执显示此次获准反馈，真正的当前场景／线索／日志从刷新后的快照恢复。回执中的 messages 不替代长期线索清单。

现有 Scala JSON 的适配要显式实现：

| 现状 | 前端处理与后续工作 |
|---|---|
| ID、Text 编码为字符串 | 解码校验后进入相应编号／文字字段 |
| Sequence、Revision 编码为 `{ value: ... }` | 在 API 解码／映射中读取 value，不能直接断言为 number |
| Scala Vector／Set 编码为数组 | 前端使用只读数组，需要时构建本地 Set；明确校验不应重复的编号 |
| ExecuteOperationMessage／InvestigateMessage 直接返回 CommandResult | 根对象含 sequence、findings、acquiredClues，没有 result 包装；不能据此伪造完整场景或日志 |
| GetGameStateResponse 只有序号、入口 ID 和线索／事实 ID | 不能直接渲染本文视图；需要安全投影补上场景、操作标签及已获线索／记录文本 |

当前后端只提供可信本地调用，未注册 HTTP／令牌边界。开始浏览器切片前必须落实这些边界；内部 StartGameMessage 当前接受 userId、sessionId、caseId、revision，按编号读取已存案件，不接受完整案件对象。浏览器接口必须从可信身份边界获得 userId，并明确会话创建与案件访问权限，不能原样公开内部消息。

## 7. 页面、请求与表现状态

```typescript
type AccessState =
  | Readonly<{ kind: "checking" }>
  | Readonly<{ kind: "allowed" }>
  | Readonly<{ kind: "needsSignIn" }>;

type UiProblem = Readonly<{
  message: string;
  recovery: "retry" | "refresh" | "signIn" | "none";
}>;

type SelectionState =
  | Readonly<{ kind: "none" }>
  | Readonly<{ kind: "point"; pointId: PointId }>;

type MutationState =
  | Readonly<{ kind: "idle" }>
  | Readonly<{ kind: "submitting"; pending: PendingMutation }>
  | Readonly<{
      kind: "awaitingSnapshot";
      pending: PendingMutation;
      acknowledgement: MutationAcknowledgement;
    }>
  | Readonly<{
      kind: "outcomeUnknown";
      pending: PendingMutation;
      problem: UiProblem;
    }>
  | Readonly<{
      kind: "rejected";
      pending: PendingMutation;
      problem: UiProblem;
    }>;

type PresentationState =
  | Readonly<{ kind: "preparing"; stamp: SnapshotStamp }>
  | Readonly<{ kind: "ready"; stamp: SnapshotStamp }>
  | Readonly<{ kind: "failed"; stamp: SnapshotStamp; problem: UiProblem }>;

type InvestigationPageState =
  | Readonly<{ kind: "loading"; sessionId: SessionId }>
  | Readonly<{
      kind: "needsSignIn";
      sessionId: SessionId;
      pending: PendingMutation | null;
    }>
  | Readonly<{ kind: "failed"; sessionId: SessionId; problem: UiProblem }>
  | Readonly<{
      kind: "ready";
      snapshot: InvestigationSnapshot;
      selection: SelectionState;
      mutation: MutationState;
      presentation: PresentationState;
    }>;
```

AccessState 仅用于应用外层的最小身份检查；调查页遇鉴权失效转 needsSignIn。不将 token、密码或真假用户资料加入页面对象。allowed 必须来自真实鉴权或明确的本地演示边界，不能把“前端写 true”当成用户有权限。

不再用多个 isLoading／isFailed／isSubmitting 布尔值表达互斥状态。ready 表示已取得一致快照；资源仍可能 preparing，因此此时保留文字日志并显示场景加载状态，暂不允许场景操作。准备失败不能从模型缺失推断线索不存在。

请求与刷新规则：

1. 同一会话首版一次只发一个变更请求。请求未决或快照未更新时禁用新的变更操作；镜头观察、打开已获线索文本等纯本地动作仍可用。
2. 超时、断网或无法解码成功响应时，可能后端已经提交，进入 outcomeUnknown。重试必须使用相同 RequestId 和完全相同载荷，包括 expectedSequence；不得生成新 ID 猜测性重复执行。
3. 收到成功回执后进入 awaitingSnapshot，刷新失败只重试快照查询，不重复提交已成功操作。快照序号必须至少达到 resultSequence 才能完成同步；可以更高。
4. 身份失效先停止变更并处理身份；已经发出的请求若结果未明，保留 pending，不当作已取消。恢复登录后先由后端重新确认会话归属；切换账号或会话不可访问时不自动重发旧请求。已知提交成功但刷新时失去身份，也可保留原 pending；重新确认时使用原 ID 的去重结果，不生成新请求。
5. 新快照必须与当前 SessionId／CaseId／CaseRevision 匹配，不能用较低 sequence 回滚较新的状态。历史请求的去重响应可能带较旧 resultSequence：它可以确认该请求已完成，但不能覆盖当前快照。
6. state stale／权限／条件不满足等由服务端明确拒绝的结果进入 rejected，并按错误语义刷新。UI recovery 仅指导界面，不提供绕过后端的权限。
7. 快照替换后，若选中 PointId 已不在可见入口中，清空选择。制作包或快照改变时重新准备对应 stamp；旧资产加载／动画回调不能把新快照错误标成 ready。

## 8. 制作期表现清单与运行时绑定

```typescript
type CameraPreset = Readonly<{
  eye: Vec3;
  target: Vec3;
  verticalFovDegrees: number;
}>;

type VisualPresetBinding = Readonly<{
  nodeId: SceneNodeId;
  allowedPresetIds: readonly VisualPresetId[];
}>;

type PickBinding = Readonly<{
  pointId: PointId;
  pickTargetKey: string;
}>;

type SceneVisualManifest = Readonly<{
  sceneBundleId: SceneBundleId;
  revision: SceneBundleRevision;
  placeId: PlaceId;
  exportedNodes: readonly SceneNodeId[];
  exportedAssets: readonly AssetId[];
  presets: readonly VisualPresetBinding[];
  picks: readonly PickBinding[];
  camera: CameraPreset;
}>;

type SceneRendererProps = Readonly<{
  snapshot: InvestigationSnapshot;
  selectedPointId: PointId | null;
  onSelectPoint: (pointId: PointId) => void;
  onPrepared: (stamp: SnapshotStamp) => void;
  onPresentationFailed: (stamp: SnapshotStamp, message: string) => void;
}>;
```

SceneVisualManifest 是已审核场景代码的接口清单，不是把全部 3D 几何、灯光或材质重新编码成后端数据库。渲染组件通过静态注册表按 `(sceneBundleId, revision)` 查找；服务器数据只能选择登记好的资源与姿态，不给出任意组件导入路径。pickTargetKey 是该场景代码导出的点击区域名，按精确名称查找，不解析文字含义。

allowedPresetIds 是每个节点的白名单且非空，例如作者可以为抽屉登记两个姿态，但下一案件新增画框姿态不必扩展全局 enum。姿态内部的部件位置、铰链、材质与过渡代码由制作代码负责。场景画面里可以表现普通装饰，但不能把未获准节点／秘密正文附带进模型资源包。

3D 交互只触发 onSelectPoint，然后页面显示后端返回的固定检查／操作选项；渲染组件不直接改玩家知识，也不自行发数据库变更。若添加“点击直接执行”，仍调用同一页面请求流程。

动画只在已经获准的目标姿态之间过渡：后端成功并取得新快照后再拉开抽屉。载入存档直接展示当前目标姿态，不从关闭姿态重演所有历史操作。动画正在过渡时可以暂时禁用相关点击；不能以动画完成作为授予线索的条件或来源。

### 制作与运行的检查

- 场景版本匹配；节点、资产、姿态及点击区域编号在各自作用域唯一，引用完整。
- 每个可见业务节点有可解析的模型／本地组件；每个返回的调查入口有获准点击绑定。允许多个入口绑定同一几何区域，不能把一个节点强制等同一条线索。
- 当前快照中的节点姿态必须在该节点白名单；关键布局符合 SceneRequirement。点的位置和半径与后端使用的坐标一致。
- 绑定的本地节点集合可以包含尚未显示的通用部件；这不意味着客户端获得访问权。只激活当前 SceneView.points 的绑定，只渲染当前获准业务节点，不预装秘密正文或秘密资产。
- CameraPreset 数值有限，FOV 在合理范围，eye 与 target 不重合；它是观察配置，不是人物地点真值。
- 空场景、空线索与没有当前操作的入口可以合法存在；明确的场景资源失败单独显示，不能静默当作业务对象不存在。

## 9. 最小书房中的实际对应

| 项目 | 制作期代码 | 后端视图／页面行为 |
|---|---|---|
| 书房、桌子和抽屉外壳 | 基础几何、灯光、材质、观察镜头 | 有效节点及登记姿态 |
| 点击抽屉 | 抽屉把手附近的点击区域绑定 PointId | 展示获准的“打开抽屉” OperationChoice |
| 抽屉拉开 | 制作两个局部姿态及过渡 | 操作获准后获取新快照，选择打开姿态 |
| 抽屉中的信 | 普通纸张几何，不含正文贴图 | 节点及调查入口在获准后出现 |
| 阅读信件 | 点击区域绑定 PointId，面板展示文本 | 提交固定路线，刷新后得到 ClueView 与公开记录 |
| 刷新网页 | 根据快照直接设定当前姿态 | 从后端恢复，不依赖浏览器上一次点击历史 |

现有 JSON 的节点位置均为零，只是契约／数据库测试夹具；制作可看的书房时必须提供布局与一致的热点坐标，不能把所有节点按零坐标叠起来。当前后端仅支持平移，首个切片先使用与该能力一致的锚点；复杂父链旋转、缩放和角色移动在对应能力实现前再补。

## 10. 文件归属与最小实现顺序

建议实现时组织如下，不提前创建空目录或一次生成本文全部文件：

```text
frontend/src/
  objects/                         真正跨页面使用的编号／坐标值
  apis/
    games/                         每个已公开后端消息对应的前端 API 与响应解码
    accounts/                      做登录能力时再建
  apiClient/                       发送、令牌转换、响应解码等共享基础设施
  pages/
    Investigation/
      index.tsx                    页面业务骨架
      objects/                     当前页状态、交互选择与场景表现清单
      hooks/                       页面请求与状态协调
      functions/                   纯解码后的映射、状态归并与选择校验
      components/
        SceneViewport/
        InteractionPanel.tsx
        InvestigationJournal.tsx
        CluePanel.tsx
  components/ui/                   多页面确实复用的 UI 基础组件
```

页面组件与 hook 就近组织；接口专用 DTO／解码放对应服务 API 附近，不把页面模型都堆进全局 objects。纯计算不直接调用 API；网络请求、资源加载与渲染副作用显式隔离。界面使用课程要求的 Tailwind；Three.js 材质参数属于场景绘制，不是另引 CSS 样式体系。

本轮只调整文档，不实现 HTTP 或前端工程。后续按以下顺序推进，其他必备页面保留在规划中：

| 步骤 | 具体产物 | 可检查结果 |
|---|---|---|
| 1. 浏览器数据边界 | 身份／本地演示边界、会话访问校验、安全一致视图和响应解码 | 合法会话读到对应修订、序号和获准内容，不能取得隐藏正文 |
| 2. 可用的调查页 | React／TypeScript／Tailwind 工程、操作区、线索区与日志区 | 先用文字入口完成打开与阅读，处理提交、刷新和重试 |
| 3. 基础几何书房 | 布局、场景清单、节点姿态和热点绑定 | 3D 点击复用同一请求流程，节点与后端可达性坐标一致 |
| 4. 恢复验收 | 浏览器刷新、旧回执、请求结果不确定与加载失败的核对 | 当前姿态与获准信息可恢复，旧响应不能覆盖新状态 |

步骤1与页面基础布局、步骤2与书房制作可以交叉推进；真实调查结果必须经过后端，模拟数据仅用于布局预览并明确标注。无需先扩充所有页面的类型或增加新的业务表。

最小闭环：获准身份 → 读取一致视图 → 选中抽屉 → 提交操作 → 刷新视图 → 阅读信件 → 线索与日志更新 → 刷新浏览器恢复。类型只在这条路径表达不了、发现权限／一致性问题或下一功能开始时局部调整。
