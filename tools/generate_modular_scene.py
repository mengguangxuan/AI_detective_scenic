"""Four independent text-code requests with a fixed assembly contract. No 3D generation API."""
import concurrent.futures
import json
from pathlib import Path
from probe_tokenhub_scene import ROOT, request, settings, save

folder=ROOT/'frontend/.build-cache/modular-scene'
folder.mkdir(parents=True,exist_ok=True)
layout=json.loads((ROOT/'tools/modular-scene/layout.json').read_text())
contract=(ROOT/'tools/modular-scene/contract.md').read_text()
roles={
 'shell':'房间壳体：旧木拼板地面、护墙、四壁和低顶，按spec.openings精确分段留真实窗/门/孔，注意侧墙孔口宽沿Z、背墙窗宽沿X。一窗、一门；窗框、玻璃、打开的百叶、门框、门板和洞口深度。通气孔通邻室，仅墙厚不加室外。返回window/shutter/door/vent实际绑定。前墙、右侧墙及其附属护墙门板和天花标userData.cutaway=true便于预览。孔位、窗位、门位不允许改变；室内6×5×2.8米，墙可向外厚0.08。不要家具。',
 'bed':'仅一张窄床与厚软拉绳。完整床架、四腿/床头柱、白罩/枕头、床垫和固定脚件。spec.size的高含拉绳。按spec.sockets精确定位枕边、绳固定点和绳末端，用柔软曲线造型。返回bed和bell_rope绑定，sockets必须与实际几何对应。不要墙、孔、其他家具。床头在Z负端；床身宽1米、长2.1米、顶约0.7米。',
 'cabinet':'完整棕色抽屉柜，侧板、背板、顶、底座、三层抽屉/铜把手。最上层抽屉独立成组，下面两层固定。活动抽屉底板与侧/后/前板相连、无顶盖，关闭与柜面齐平，打开沿+Z约0.42米，箱底始终与面板联动。不要让侧壁/底板越过面板；不要任何线索/纸条/物品。返回chest、drawer绑定和drawer motion；closed等于初始局部位置。',
 'furnishings':'可复用家具模块，根据spec.kind生成一种物件。chair：四腿、座面、藤编弯靠背、横撑扶手；dressing_table：台面腿、抽屉外形、带圆弧或椭圆木框镜面与支架，避免像现代显示器；fireplace：炉台、侧柱、顶梁、深色凹入炉膛/炉底，内部不能被浅色墙面盖住，根在其地面投影中心；rug：带边纹的方形薄地毯，高不超过0.03米。绑定key：chair/dressing_table/fireplace/rug随kind匹配。不要额外家具或装饰物，全部使用spec尺寸。'
}

def generate(name):
    target=folder/(name+'.response.json')
    if target.exists():
        raise RuntimeError('Saved candidate exists for '+name+'; no automatic duplicate call')
    specs=[i for i in layout['instances'] if i['module']==name]
    prompt=contract+'\n你只负责'+roles[name]+'\n输入spec示例（全局position由宿主处理，你不能使用它进行平移）：'+json.dumps(specs,ensure_ascii=False)+'\n仅返回JSON {"code":"完整ES模块源码","notes":["说明"]}。必须导出buildAsset。共用kit减少重复，源码控制在150行/2500输出tokens以内，省略长注释；不要import，不要全屋代码。接口与尺寸优先于装饰，不用占位代码。'
    save(folder/(name+'.prompt.json'),{'system':'你是独立的Three.js部件程序美术师。只实现提供的固定契约，输出JSON。','user':prompt})
    c,_=settings()
    r=request('/chat/completions',{'model':c['model'],'messages':[{'role':'system','content':'你是独立的Three.js部件程序美术师。只实现提供的固定契约，输出JSON。'},{'role':'user','content':prompt}],'max_tokens':4500,'stream':False,'thinking':{'type':'disabled'},'response_format':{'type':'json_object'}},timeout=180)
    save(target,r)
    choice=r['choices'][0]
    if choice.get('finish_reason')!='stop':raise RuntimeError(name+' incomplete; not executable')
    x=json.loads(choice['message']['content'])
    (folder/(name+'.candidate.js')).write_text(x['code'])
    save(folder/(name+'.notes.json'),x.get('notes',[]))
    return {'module':name,'finish':'stop','usage':r.get('usage'),'characters':len(x['code'])}

with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
    futures={pool.submit(generate,name):name for name in roles}
    for future in concurrent.futures.as_completed(futures):
        try: print(json.dumps(future.result(),ensure_ascii=False),flush=True)
        except Exception as e: print(json.dumps({'module':futures[future],'error':type(e).__name__,'message':str(e)},ensure_ascii=False),flush=True)
