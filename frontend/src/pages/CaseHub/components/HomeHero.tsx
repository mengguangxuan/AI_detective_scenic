import InvestigationLink from '../../../app/components/InvestigationLink';
import { navigate } from '../../../app/hooks/usePathname';

export default function HomeHero({ canContinue }: { canContinue: boolean }) {
  return <section aria-label="侦探剧场首页主视觉" className="relative isolate my-10 overflow-hidden rounded-3xl border border-amber-200/20 bg-[#0b1414] lg:min-h-[36rem]">
    <div className="relative aspect-[1672/941] lg:absolute lg:inset-0 lg:aspect-auto">
      <img src="/images/truth-only-one-v1.png" alt="原创侦探与年轻助手的雾夜插画，配文：真相只有一个" width={1672} height={941} fetchPriority="high" decoding="async" draggable={false} className="h-full w-full object-cover object-top"/>
      <div aria-hidden="true" className="absolute inset-x-0 bottom-0 h-12 bg-linear-to-t from-[#0b1414] to-transparent lg:hidden"/>
    </div>
    <div aria-hidden="true" className="pointer-events-none absolute inset-0 hidden bg-linear-to-r from-[#081210]/95 via-[#081210]/35 to-transparent lg:block"/>
    <div className="relative z-10 p-7 pt-4 sm:p-10 lg:flex lg:min-h-[36rem] lg:w-[48%] lg:flex-col lg:justify-center lg:p-10">
      <p className="text-xs tracking-widest text-amber-200">观察 · 问询 · 推理</p>
      <h1 className="mt-5 text-balance text-3xl font-medium leading-snug text-stone-50 sm:text-4xl lg:text-5xl">真相，等你亲自发现。</h1>
      <p className="mt-6 max-w-xl text-base leading-8 text-stone-200">走进现场，留意不起眼的物件，听听每个人的说法。用你发现的材料，解释事情究竟怎样发生。</p>
      <div className="mt-7 flex flex-wrap gap-4"><InvestigationLink href="/cases" className="rounded-lg bg-amber-200 px-6 py-3 font-medium text-stone-950 hover:bg-amber-100">进入案件区</InvestigationLink>{canContinue ? <button type="button" onClick={() => navigate('/case')} className="rounded-lg border border-white/40 bg-[#0b1414]/70 px-6 py-3 text-stone-100 hover:bg-[#182421]">继续当前调查</button> : null}</div>
    </div>
  </section>;
}
