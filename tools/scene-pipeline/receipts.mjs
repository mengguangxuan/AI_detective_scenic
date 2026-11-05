import fs from 'node:fs';
import path from 'node:path';
import {isDeepStrictEqual} from 'node:util';
// Reuse only complete recorded answers for exactly the same raw input, plan and
// module/assembler payload. A file's mere existence never authorizes reuse.
export function savedAnswer(folder,payload){
 const directory=path.join(folder,'requests');
 if(!fs.existsSync(directory))return null;
 const files=fs.readdirSync(directory).filter(name=>name.endsWith('.response.json')).sort((a,b)=>Number(b.split('-')[0])-Number(a.split('-')[0]));
 for(const name of files){
  try{
   const request=JSON.parse(fs.readFileSync(path.join(directory,name.replace('.response.json','.request.json')),'utf8'));
   const user=request.messages?.find(m=>m.role==='user')?.content;
   if(typeof user!=='string'||!isDeepStrictEqual(JSON.parse(user),payload))continue;
   const response=JSON.parse(fs.readFileSync(path.join(directory,name),'utf8'));
   const text=typeof response.content==='string'?response.content:response.choices?.[0]?.finish_reason==='stop'?response.choices[0].message?.content:null;
   if(typeof text!=='string')continue;
   const answer=JSON.parse(text);
   if(typeof answer.code==='string'&&answer.code.length<=90000)return{answer,source:name};
  }catch{/* An absent, failed or invalid record cannot become a cached answer. */}
 }
 return null;
}
