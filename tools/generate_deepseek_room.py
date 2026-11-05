"""Explicit text-model scene-code experiment. Saves a candidate for human review, never executes it."""
import json
from pathlib import Path
from probe_tokenhub_scene import ROOT, request, settings, save

folder = ROOT / 'frontend/.build-cache/deepseek-room'
folder.mkdir(parents=True, exist_ok=True)
prompt = '''请编写一个精致、完整、可交互部件分离的Three.js 0.180卧室。输出JSON，字段code(完整ES module源码字符串)、designNotes(字符串数组)。这是制作阶段的候选代码，将经人工审查后预览。
唯一导出 export function createRoom(THREE)。只使用传入的THREE和标准数学，可用document.createElement('canvas')与CanvasTexture制作程序纹理；禁止import、网络、存储、eval、Function、HTML、事件、计时器、全局修改。不要创建renderer/camera/controls/UI。所有几何通过new THREE.*创建。
返回 {root: THREE.Group, objects: {bed, chest, drawer, dressing_table, chair_1, chair_2, rug, fireplace, window, shutter, bell_rope, vent}, drawerMotion:{closed:[x,y,z],opened:[x,y,z]}, roomSize:[宽,高,深], notes:字符串数组}。objects里的值必须是对应实际Object3D且挂在root里；drawer必须是独立可平移的完整抽屉组，不是整柜。drawerMotion使用drawer局部坐标。物体对象名字与key相同，便于绑定检查；不能用标签/文字代替造型。root名deepseek_bedroom。
要求：1883年前后英国乡间朴素旧宅卧室，低顶、旧橡木地板和护墙，壁炉，棕色抽屉柜，一张且仅一张窄单人床及白罩，窗左梳妆台，两把藤椅，中央方形地毯。不要添加别的大件或人、动物、文字。房间约6米乘5米，高2.8米，Y向上，地板顶面y=0。制作木纹、地板拼缝、护墙板、门框、窗框/玻璃/窗格/百叶窗、床腿/床柱/床垫/枕头/白罩、柜子侧板背板/抽屉箱/把手、梳妆台镜子与支架、藤椅腿/横撑/弯背/编织纹理、壁炉边框炉膛、地毯边纹等细节，避免几个孤立方块。使用合理面数，不要逐像素生成网格。房间完整四壁与顶，但前墙和顶设置userData.cutaway=true，供预览隐藏看内部；背墙在z负端。窗和门必须有真实开口，不是实墙贴平面；只允许一扇窗、一扇通走廊门。家具不穿墙、保留中间通路。木纹和布纹可程序CanvasTexture。主色暖木、奶油白与暗红，MeshStandardMaterial。
案件固定约束：床靠靠近通气孔的侧墙，床头有厚软拉绳垂至枕边，绳上方同一侧墙有一个通向相邻室的小通气孔，不能通向室外。用分段墙形成孔洞，并加孔框/格栅。只生成孔口和相邻墙厚，不暴露隐藏剧情内容。床固定在地板上可有固定脚件。不要生成蛇、凶器或补造线索。
交互演示：抽屉起初关闭，宿主可平移打开并看到内部；不要在抽屉里放任何线索、纸条或文字。床/绳/孔/窗应有单独对象，支持宿主射线选择。家具完整性和美术造型优先，所有对象的存在与位置一致。root包含合理AmbientLight/HemisphereLight/DirectionalLight便于预览，方向光不过曝；不需要真实阴影贴图。将设计补充与原著事实区别记在notes。源码务必可直接解析运行，不用占位代码。'''
(folder / 'generation-prompt.txt').write_text(prompt, encoding='utf-8')
import argparse
args = argparse.ArgumentParser(description=__doc__)
args.add_argument('--continue-incomplete', action='store_true', help='Continue the saved truncated candidate once; never submit a 3D generation.')
a = args.parse_args()
c, _ = settings()
prefix = ''
output = folder / 'generation-response.json'
if a.continue_incomplete:
    first = json.loads(output.read_text(encoding='utf-8'))
    if first['choices'][0].get('finish_reason') != 'length':
        raise RuntimeError('Only a saved length-truncated candidate can be continued')
    # Recover an unfinished outer JSON string without executing any candidate code.
    prefix = json.loads(first['choices'][0]['message']['content'] + '"}')['code']
    prompt += '\n以下函数前缀尚未闭合。只续写最后一行之后的代码，补齐剩余对象与返回值，不重复前缀，剩余代码控制在6000tokens内：\n' + prefix
    output = folder / 'continuation-response.json'
if output.exists():
    raise RuntimeError('Saved output exists; inspect it before making another paid text request')
result = request('/chat/completions', {
    'model': c['model'], 'messages': [
        {'role': 'system', 'content': '你是Three.js场景程序美术师。输出JSON对象，code字段为源码，不要Markdown。续写模式不重复前缀。'},
        {'role': 'user', 'content': prompt}],
    'stream': False, 'max_tokens': 10000, 'thinking': {'type': 'disabled'},
    'response_format': {'type': 'json_object'}}, timeout=180)
save(output, result)
choice = result['choices'][0]
if choice.get('finish_reason') != 'stop':
    raise RuntimeError('Candidate incomplete; saved privately, not executable')
candidate = json.loads(choice['message']['content'])
if not isinstance(candidate.get('code'), str) or not candidate['code'].strip():
    raise RuntimeError('No candidate code returned')
(folder / 'candidate.js').write_text(prefix + '\n' + candidate['code'], encoding='utf-8')
save(folder / 'design-notes.json', candidate.get('designNotes', []))
print(json.dumps({'model': c['model'], 'codeBytes': len(candidate['code'].encode()), 'usage': result.get('usage'), 'status': 'candidate_saved_for_review'}, ensure_ascii=False))
