"""Explicit repair of failed modules; other module files are untouched."""
import concurrent.futures
import json
from probe_tokenhub_scene import ROOT, request, settings, save
folder=ROOT/'frontend/.build-cache/modular-scene'
reports=json.loads((folder/'preflight.json').read_text())
extra={
'shell':'size是外包络，不是墙内尺寸。室内固定6×5、高2.8；墙厚向外0.08，墙中心x=±3.04、z=±2.54，外界不得越过spec.size。地板顶面y=0（允许底面-0.025）。窗必须在后墙z=-2.5，门必须在右墙x=3，而不是前墙。通气孔在左墙x=-3。按给定坐标准确分段开洞，任何框不能用实心板封住洞口，不能生成贯穿全屋的厚板。初始化registry必须在任何调用前。不要返回复杂自评。',
'bed':'删掉失败尝试和重建过程，直接一次性生成床组与绳组；不要while，不要重复const。枕边socket是枕头左侧边缘附近的接点，不是枕头中心。枕头要有床垫支撑，床垫/白罩顶约0.73，枕头中心约y=.78，枕头中心x=0，宽约.8，不能因使用socket.x=-.35作为枕头中心而超出包络。绳几何放独立group，全部坐标受包络约束。',
'cabinet':'包络包括把手。适当缩小柜体深度以容纳把手，而不是修改spec。打开抽屉可超出关闭包络，关闭状态所有部件必须在尺寸以内。保持三层抽屉、最上层独立活动。',
'furnishings':'禁止用同名变量同时表示材质和尺寸，材质必须取自kit.materials。所有kind都要实际运行：chair、dressing_table、fireplace、rug；它们必须各自满足尺寸和绑定。移除无用new Mesh。socktes用普通对象或{}即可，不要动态计算属性。镜框与所有mesh不能越界。地毯几何顶面不得穿地。'
}
def repair(row):
 name=row['module']; saved=folder/(name+'.repair.response.json')
 if saved.exists(): raise RuntimeError('Explicit repair already saved for '+name)
 prompt=json.loads((folder/(name+'.prompt.json')).read_text())['user']
 code=(folder/(name+'.candidate.js')).read_text()
 (folder/(name+'.initial.js')).write_text(code)
 payload=prompt+'\n这份候选未通过程序检查，请仅重写本模块完整源码，不要改契约或布局。错误：'+row['error']+'\n补充约束：'+extra[name]+'\n失败源码：\n'+code+'\n返回JSON {"code":"完整替换模块","notes":[]}。不要保留失败过程，避免长注释。'
 c,_=settings();r=request('/chat/completions',{'model':c['model'],'messages':[{'role':'system','content':'你是Three.js部件代码修复者。根据检测错误修复一个模块，不能修改尺寸、宿主或其他模块。只输出JSON。'},{'role':'user','content':payload}],'stream':False,'max_tokens':5000,'thinking':{'type':'disabled'},'response_format':{'type':'json_object'}},timeout=180)
 save(saved,r);choice=r['choices'][0]
 if choice.get('finish_reason')!='stop':raise RuntimeError(name+' repair incomplete')
 x=json.loads(choice['message']['content']);(folder/(name+'.candidate.js')).write_text(x['code']);print(json.dumps({'module':name,'usage':r.get('usage'),'status':'repair_saved'},ensure_ascii=False),flush=True)
failed=[r for r in reports if not r['passed'] and r['module'] in extra]
with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
 futures=[pool.submit(repair,r) for r in failed]
 for future in concurrent.futures.as_completed(futures):future.result()
