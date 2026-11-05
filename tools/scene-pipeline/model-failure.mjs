// Only finite codes and scalar metadata are used in user-visible messages.
const messages={
 ProviderHttpError:'模型供应商返回异常状态',ResponseTooLarge:'模型响应超过读取上限',
 InvalidEnvelope:'模型响应的外层JSON格式不符合协议',IncompleteOutput:'模型输出未完整结束',
 EmptyOutput:'模型未返回有效正文',ContentTooLarge:'模型正文超过长度上限',
 Timeout:'模型请求超时',InvalidInput:'模型请求参数或截图校验失败',TransportError:'无法连接模型服务',
};
export function modelFailureDetails(value){
 if(!value||typeof value!=='object'||!Object.hasOwn(messages,value.code))return null;
 const integer=v=>Number.isSafeInteger(v)&&v>=0?v:null;
 return{code:value.code,providerStatus:integer(value.providerStatus),finishReason:['stop','length','content_filter','tool_calls','function_call','other'].includes(value.finishReason)?value.finishReason:null,responseBytes:integer(value.responseBytes),contentChars:integer(value.contentChars),totalTokens:integer(value.totalTokens)??0,maxTokens:integer(value.maxTokens),partialContent:typeof value.partialContent==='string'?value.partialContent.slice(0,90000):null};
}
export function modelFailureMessage(failure){
 if(!failure)return 'model service unavailable';
 const details=[failure.providerStatus?`HTTP ${failure.providerStatus}`:null,failure.finishReason?`finish_reason=${failure.finishReason}`:null,failure.maxTokens?`max_tokens=${failure.maxTokens}`:null].filter(Boolean);
 return messages[failure.code]+(details.length?'（'+details.join('，')+'）':'')+'；任务已停止，未自动重试';
}
