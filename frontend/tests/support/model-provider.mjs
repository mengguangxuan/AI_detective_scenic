// Local protocol fixture only. No external model calls and no real credentials.
import http from 'node:http';
const approvedKey = 'Bearer dummy-browser-key';
const server = http.createServer(async (request, response) => {
  if (request.url === '/shutdown' && request.method === 'POST') {
    response.end('stopping'); server.close(); return;
  }
  if (request.url !== '/chat/completions' || request.method !== 'POST') { response.writeHead(404).end(); return; }
  if (request.headers.authorization !== approvedKey) { response.writeHead(401).end('{}'); return; }
  try {
    let raw = '';
    for await (const chunk of request) raw += chunk;
    const body = JSON.parse(raw);
    const context = JSON.parse(body.messages.find(message => message.role === 'user').content);
    if (context.question.includes('接口故障测试')) { response.writeHead(503).end('{"error":"fixture unavailable"}'); return; }
    let id;
    if (/教堂|凭什么|时间依据/.test(context.question)) id = 'mary_time_basis';
    else if (/钟|准不准/.test(context.question)) id = 'clement_habit';
    else if (/怎么发现/.test(context.question)) id = 'clement_discovery';
    const allowed = new Set((context.disclosures ?? context.answers).map(answer => answer.id));
    if (context.witness.name === '梅尔切特' && /医生|死因|死亡时间/.test(context.question)) id = 'police_medical';
    if (context.witness.name === '安妮·普罗瑟罗') {
      id = /枪.*哪|来源/.test(context.question) ? 'anne_gun' : /时间|几点/.test(context.question) ? 'anne_time' : 'anne_statement';
    }
    if (context.witness.name === '梅尔切特' && /两份|两人/.test(context.question)) id = 'police_two_statements';
    if (context.witness.name === '劳伦斯·雷丁') {
      id = /安妮.*陈述|安妮.*认罪/.test(context.question) ? 'redding_anne_reply' : /医生|死亡时间/.test(context.question) ? 'redding_medical_reply' : /钟/.test(context.question) ? 'redding_clock' : /枪/.test(context.question) ? 'redding_gun' : 'redding_account';
    }
    if (context.witness.name === '马普尔小姐') {
      id = /供述|说法/.test(context.question) ? 'marple_account_reply' : /声|响/.test(context.question) ? 'marple_sound' : /离开/.test(context.question) ? 'marple_departure' : 'marple_visitors';
    }
    if (context.witness.name === '上校的贴身男仆') id = 'valet_gun';
    if (context.witness.name === '斯通博士') id = /四十|离开|喝酒/.test(context.question) ? 'stone_inn' : 'stone_walk';
    if (context.witness.name === '哈特内尔小姐') id = /神情|样子|怎么样/.test(context.question) ? 'hartnell_manner' : 'hartnell_visit';
    if (context.witness.name === '玛丽' && /送鱼/.test(context.question)) id = 'mary_fish_time';
    else if (context.witness.name === '玛丽' && /树林.*响|响声.*什么时候/.test(context.question)) id = 'mary_sound';
    if (context.witness.name === '梅尔切特' && /对照枪/.test(context.question)) id = 'police_gun_check';
    if (context.witness.name === '梅尔切特' && /对照行踪/.test(context.question)) id = 'police_movement_check';
    if (context.witness.name === '梅尔切特' && /对照响声/.test(context.question)) id = 'police_sound_check';
    if (context.witness.name === '劳伦斯·雷丁') {
      if (allowed.has('redding_revised')) id = /钟|纸|尸体/.test(context.question) ? 'redding_scene_revised' : /枪/.test(context.question) ? 'redding_gun_revised' : 'redding_revised';
      else if (/行踪.*检查|六点三刻/.test(context.question)) id = 'redding_time_challenge';
    }
    if (context.witness.name === '安妮·普罗瑟罗') {
      if (allowed.has('anne_revised')) id = /窗|行踪/.test(context.question) ? 'anne_visit_revised' : /枪/.test(context.question) ? 'anne_gun_revised' : 'anne_revised';
      else if (/男仆|核验/.test(context.question)) id = 'anne_gun_challenge';
      else if (/雷丁.*补充/.test(context.question)) id = 'anne_redding_challenge';
    }
    if (context.witness.name === '梅尔切特' && /四份供述/.test(context.question)) id = 'police_revised_review';
    let reply = (context.disclosures ?? context.answers).find(answer => answer.id === id)?.text ?? '这件事我也不清楚。';
    if (context.witness.name === '克莱门特牧师' && context.question.includes('斯莱克是谁')) reply = '斯莱克是调查本案的警探，昨晚来过这里。我跟他说话时，他很不耐烦。';
    if (context.witness.name === '克莱门特牧师' && context.question.includes('什么时候离开')) reply = '五点半接到电话，打完给上校家的电话后我就离开了。原本争取六点半回来，实际快七点才到家。';
    if (context.witness.name === '克莱门特牧师' && context.question.includes('病人是谁')) reply = '下农场的阿博特先生。电话里说他快不行了，我到了才发现不是那么回事。';
    if (context.witness.name === '玛丽' && !['mary_sound', 'mary_fish_time'].includes(id) && /异响|响声/.test(context.question)) reply = '我记得一声砰响，当时以为在树林里，具体几点我没留意。';
    if (context.witness.name === '梅尔切特' && !['police_gun_check', 'police_movement_check', 'police_sound_check'].includes(id) && /怎么看|材料/.test(context.question) && context.shownEvidence.length) reply = '你出示的这份座钟记录写的是18:22。还得问清它平时走得准不准。';
    if (context.witness.name === '梅尔切特' && /核验结果|核验.*多久|医生到场.*进展/.test(context.question)) {
      const medical = /医生到场/.test(context.question);
      const title = medical ? '医生到场与初查记录' : '手枪与弹头核验';
      const clue = medical ? 'clue_medical_visit_record' : 'clue_gun_verification';
      reply = context.shownEvidence.find(item => item.id === clue)?.text ?? context.knowledge.find(item => item.startsWith(title + '：')) ?? '这项还未受理。';
      id = undefined;
    }
    const decision = context.question.includes('越界测试') ? { disclosureIds: ['future_confession'], reply: 'DO_NOT_RENDER_FUTURE' }
      : context.disclosures ? { reply, disclosureIds: id && allowed.has(id) ? [id] : [] } : { answerIds: id && allowed.has(id) ? [id] : [] };
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ choices: [{ finish_reason: 'stop', message: { content: JSON.stringify(decision) } }] }));
  } catch { response.writeHead(400).end('{}'); }
});
server.listen(8091, '127.0.0.1', () => console.log('[model fixture ready] 127.0.0.1:8091'));
