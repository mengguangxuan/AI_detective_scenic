# 独立生成模块的固定接口（实验）

本次布局已经用户确认，见layout.json。此为制作工具实验，尚未接入案件发布流程。

```js
export function buildAsset(THREE, spec, kit) {
  // 仅构造本部件，root保持平移0、旋转0、缩放1。
  return {
    root,                        // THREE.Group，仅包含自己的几何
    bindings: { key: object },    // spec.bindings要求的可点击Object3D，均须属于root
    sockets: { key: [x,y,z] },    // 本地坐标接口；床模块须返回规定的三个锚点
    motions: {                   // 无活动部件时为空对象
      drawer: { binding: 'drawer', closed:[x,y,z], opened:[x,y,z] }
    }
  };
}
```

统一使用米、Y向上、+Z为家具正面；本地原点在地面投影中心。组装器拥有位置和旋转，部件不能修改其他部件、场景、灯光、相机或UI。默认包络为x±宽/2、z±深/2、y∈[0,高]，允许0.03米数值余量。所有必需绑定必须有实际几何，不能返回空组充数；主绑定可以等于root。

kit.box(parent,[宽,高,深],[x,y,z],material)返回Mesh。
kit.cylinder(parent,[顶半径,底半径,高],[x,y,z],material,[rx,ry,rz]可选)返回Mesh。
kit.tube(parent,[[x,y,z],...],半径,material)返回Mesh。
kit.materials提供wood、darkWood、plaster、cloth、wicker、rug、metal、brass、glass、stone、soot、mirror。

禁止import、网络、DOM、存储、eval、计时器、事件及修改全局；纹理由kit统一提供。建模函数须在有限步骤内返回。程序检查语法、约定接口、根变换、绑定归属、有限包络、实际几何和活动部件；这些检查不能证明任意模型代码安全，也不能证明视觉／事实合理。

组装器不修正模型几何。失败输出留候选，按错误反馈给相应模块生成；通过的代码登记后才进入独立本机预览。正式产品仍需构建／隔离边界。独立审核请求只接收确认布局、程序检测结果及实际多视角截图，不接收生成对话或模型自评；问题与不确定项均保留。模型审核完成后再提供用户预览，未通过质量审核不得标成已验收。
