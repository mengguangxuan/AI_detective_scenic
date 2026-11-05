export const limits=Object.freeze({maxRounds:10,maxCalls:8+10*8});
export function resumeState(job,artifactsPresent){
  if(job.stage==='passed')return{run:false,startRound:job.round??0,issues:[]};
  const startRound=job.round??0;
  if(!Number.isInteger(startRound)||startRound<0)throw new Error('Invalid saved review progress');
  if(startRound>0&&!artifactsPresent)throw new Error('Saved scene files missing; refusing to regenerate completed work');
  return{run:startRound<limits.maxRounds,startRound,issues:job.issues??[]};
}
