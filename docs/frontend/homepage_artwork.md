# 首页侦探插画

日期：2026-11-01。根据用户要求使用imagegen技能的内置图像生成工具制作，非CLI调用。用户要求配文“真相只有一个”，融合经典英伦侦探和少年侦探氛围，不直接复用福尔摩斯、柯南等现有角色。

成品：[truth-only-one-v1.png](../../frontend/public/images/truth-only-one-v1.png)，运行路径`/images/truth-only-one-v1.png`。完整生成图直接复制到项目，未重绘或覆盖既有素材。画面是原创成年侦探与年轻助手、雾夜城市和调查桌，深绿／炭黑配古金；左侧留给网页文字，右上配文已目视核对。没有使用参考图、现成IP素材、标志或案件剧透。

首页HomeHero组件桌面端作为背景使用，保留顶部配文并用左侧渐变衬托HTML文字；窄屏完整展示插画，介绍与按钮排在下方。图片本身含配文，替代文本提供同一句话；首屏图片优先加载，资源已存项目，不依赖生成工具目录。主文案、进入案件区及继续调查的行为不变。

## 最终生成提示词

```text
Use case: illustration-story
Asset type: original illustrated homepage background for a Chinese detective theatre game, wide landscape about 16:9.
Create a premium cinematic mystery key visual that blends the atmosphere of classic British deduction and contemporary youthful sleuth stories, inspired broadly by Sherlock Holmes and Detective Conan WITHOUT recreating either character, their recognizable silhouettes, costumes, signature props combinations, logos, or an existing scene.
Scene: rain-wet old city at night, mist, distant clock tower and warm window lights, a subtle investigation desk with scattered handwritten papers and a magnifying glass. Two entirely original sleuths on the RIGHT: an adult investigator in a plain dark long coat, no deerstalker or pipe, and a young apprentice in a muted moss-green jacket, no blue blazer, red bow tie, distinctive spiky hair, signature glasses, or gadgets. Calm alert poses, elegant semi-realistic editorial illustration, painterly fine grain, sophisticated chiaroscuro, deep forest green and charcoal with restrained antique gold highlights. No violence, no blood, no case spoilers.
Composition: the LEFT 45 percent is quiet very dark atmospheric negative space suitable for live webpage copy and buttons. The main characters occupy the lower-right half, safely inset from all edges. Keep the TOP-RIGHT area available for a single highly legible Chinese tagline, with ample margin so neither faces nor text are cut off in a wide web banner.
Text (verbatim), rendered cleanly and accurately in tasteful large antique-gold Chinese display typography: “真相只有一个”. Exactly these six Chinese characters, no quotation marks. Place this text in the upper-right quadrant above the characters, fully inside the image. No other words, no English, no logos, no watermark. This is the finished illustration asset, not a website mockup; do not draw UI, buttons or borders.
Opaque dark background, high quality and strong composition.
```
