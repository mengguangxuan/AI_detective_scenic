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
    if (context.witness.name === '劳伦斯·雷丁') {
      id = /医生|死亡时间/.test(context.question) ? 'redding_medical_reply' : /钟/.test(context.question) ? 'redding_clock' : /枪/.test(context.question) ? 'redding_gun' : 'redding_account';
    }
    if (context.witness.name === '马普尔小姐') {
      id = /供述|说法/.test(context.question) ? 'marple_account_reply' : /声|响/.test(context.question) ? 'marple_sound' : /离开/.test(context.question) ? 'marple_departure' : 'marple_visitors';
    }
    let reply = (context.disclosures ?? context.answers).find(answer => answer.id === id)?.text ?? '这件事我也不清楚。';
    if (context.question.includes('斯莱克是谁')) reply = '斯莱克是调查本案的警探，昨晚来过这里。我跟他说话时，他很不耐烦。';
    if (context.question.includes('什么时候离开')) reply = '五点半接到电话，打完给上校家的电话后我就离开了。原本争取六点半回来，实际快七点才到家。';
    if (context.question.includes('病人是谁')) reply = '下农场的阿博特先生。电话里说他快不行了，我到了才发现不是那么回事。';
    if (context.witness.name === '玛丽' && /异响|响声/.test(context.question)) reply = '我记得一声砰响，当时以为在树林里，具体几点我没留意。';
    if (context.witness.name === '梅尔切特' && /怎么看|材料/.test(context.question) && context.shownEvidence.length) reply = '你出示的这份座钟记录写的是18:22。还得问清它平时走得准不准。';
    const decision = context.question.includes('越界测试') ? { disclosureIds: ['future_confession'], reply: 'DO_NOT_RENDER_FUTURE' }
      : context.disclosures ? { reply, disclosureIds: id && allowed.has(id) ? [id] : [] } : { answerIds: id && allowed.has(id) ? [id] : [] };
    response.setHeader('Content-Type', 'application/json');
    response.end(JSON.stringify({ choices: [{ finish_reason: 'stop', message: { content: JSON.stringify(decision) } }] }));
  } catch { response.writeHead(400).end('{}'); }
});
server.listen(8091, '127.0.0.1', () => console.log('[model fixture ready] 127.0.0.1:8091'));
