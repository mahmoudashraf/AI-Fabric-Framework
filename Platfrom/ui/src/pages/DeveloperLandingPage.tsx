import { useEffect, useState } from 'react'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import ArrowOutwardRounded from '@mui/icons-material/ArrowOutwardRounded'
import CodeRounded from '@mui/icons-material/CodeRounded'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import RedeemOutlined from '@mui/icons-material/RedeemOutlined'
import SchoolOutlined from '@mui/icons-material/SchoolOutlined'
import { developerConsoleHref, developerEntryPath } from '../developer/developerEntry'
import { DeveloperStarterCards } from '../developer/DeveloperStarterCards'
import { DeveloperExplorerDialog, developerCapabilities, developerChannels, developerSteps, type DeveloperTopic } from '../developer/DeveloperExplorerDialog'
import './DeveloperLandingPage.css'


export function DeveloperLandingPage({ authenticated = false }: { authenticated?: boolean }) {
  const asset = `${import.meta.env.BASE_URL}products/`
  const [selected, setSelected] = useState<DeveloperTopic | null>(null)
  useEffect(() => {
    const title = document.title
    document.title = 'Smart Brain for your app | LoomAI Developers'
    return () => { document.title = title }
  }, [])
  return <div className="dev-landing" data-developer-landing>
    <header className="dev-header"><span className="dev-brand"><img src={`${asset}box.svg`} width="28" height="28" alt="" /><strong>LoomAI</strong></span><span className="dev-for-developers">FOR DEVELOPERS</span>
      <nav aria-label="Developer account"><a href={developerConsoleHref(developerEntryPath('/sign-in'))}>Sign in</a><a className="dev-button dev-console-button" href={developerConsoleHref(developerEntryPath(authenticated ? '/home' : '/sign-in'))}>Open Console<ArrowOutwardRounded /></a></nav></header>
    <main>
      <section className="dev-hero" aria-labelledby="dev-title"><p className="dev-eyebrow">YOUR APP. YOUR AI.</p><h1 id="dev-title">Smart Brain for your app.</h1><p className="dev-hero-copy">Connect your data, choose how it thinks, and give your project useful AI.</p>
        <div className="dev-hero-actions"><a href="#developer-brains" className="dev-button">Create a Smart Brain<ArrowForwardRounded /></a><a href="https://loomai.pro/demos/dealership-ai" target="_blank" rel="noopener noreferrer" className="dev-demo"><PlayCircleOutlineRounded />Explore a demo</a></div>
        <div className="dev-brain-map">
          <svg className="dev-connections" viewBox="0 0 1200 430" preserveAspectRatio="none" aria-hidden="true"><g>{[68,166,264,362].map((y, index) => <g key={y}><path d={`M305 ${y} C400 ${y}, 410 ${140 + index * 34}, 490 ${140 + index * 34}`} /><path d={`M895 ${y} C800 ${y}, 790 ${140 + index * 34}, 710 ${140 + index * 34}`} /><circle cx="305" cy={y} r="3" /><circle cx="895" cy={y} r="3" /></g>)}</g></svg>
          <div className="dev-capabilities dev-capabilities-left">{developerCapabilities.slice(0, 4).map(item => <button key={item.id} className="dev-capability" onClick={() => setSelected(item.id)} aria-haspopup="dialog"><item.icon /><span><strong>{item.name}</strong><small>{item.summary}</small></span><ArrowOutwardRounded className="dev-capability-open" /></button>)}</div>
          <div className="dev-brain"><img src={`${import.meta.env.BASE_URL}developer/smart-brain.png`} alt="Sculptural silver AI brain with teal neural connections on a compute base" width="1280" height="1280" fetchPriority="high" /><strong>My project brain</strong><span>Configured in LoomAI</span></div>
          <div className="dev-capabilities dev-capabilities-right">{developerCapabilities.slice(4).map(item => <button key={item.id} className={`dev-capability${item.id === 'human' ? ' dev-capability-human' : ''}`} onClick={() => setSelected(item.id)} aria-haspopup="dialog"><item.icon /><span><strong>{item.name}</strong><small>{item.summary}</small></span><ArrowOutwardRounded className="dev-capability-open" /></button>)}</div>
        </div>
        <div className="dev-app-connection"><button className="dev-endpoint" onClick={() => setSelected('backend')} aria-label="View API integration" aria-haspopup="dialog"><CodeRounded /><strong>Your app or side project</strong><code>HTTPS API · your assigned Brain</code></button><div className="dev-app-targets">{developerChannels.map(({ id, name, icon: Icon }) => <button key={id} onClick={() => setSelected(id)} aria-haspopup="dialog"><Icon />{name}<ArrowOutwardRounded className="dev-channel-open" /></button>)}</div></div>
      </section>
      <section className="dev-build-steps" aria-labelledby="dev-steps-title"><h2 id="dev-steps-title">Start small. Add capabilities as you build.</h2><ol>{developerSteps.map(({ id, name, icon: Icon }, index) => <li key={id}><button className="dev-step-button" onClick={() => setSelected(id)} aria-haspopup="dialog"><b>{index + 1}</b><Icon /><span>{name}</span>{index < 3 ? <ArrowForwardRounded className="dev-step-arrow" /> : <ArrowOutwardRounded className="dev-step-arrow" />}</button></li>)}</ol></section>
      <section className="dev-programme" aria-label="Developer programme"><p className="dev-eyebrow">DEVELOPER PROGRAMME</p><div className="dev-offer"><div><RedeemOutlined /><strong>Free for 1 year</strong><span className="dev-staging-badge">Developer staging</span></div><div><SchoolOutlined /><strong>Training by LoomAI included</strong></div></div><p>Build your smart app, learn the tools, and make it your own.</p><small>Developer platform access for one year. Staging only. Existing model-usage and infrastructure allowances apply.</small></section>
      <section className="dev-starters" id="developer-brains" aria-labelledby="dev-starters-title"><h2 id="dev-starters-title">What will you build first?</h2><p>Two ready-to-adapt starting points. The same configurable Smart Brain.</p><DeveloperStarterCards /><p className="dev-closing">Connect your own systems. Keep control of your project.</p><a className="dev-training" href={developerConsoleHref(developerEntryPath('/support?new=1'))}><SchoolOutlined />Ask LoomAI about developer training<ArrowForwardRounded /></a></section>
    </main>
    <footer className="dev-footer"><span className="dev-brand"><img src={`${asset}box.svg`} width="23" height="23" alt="" /><strong>LoomAI</strong></span><span>For developers</span><a href="https://loomai.pro/products/loomai-platform">Explore the platform</a><a href={developerConsoleHref(developerEntryPath('/sign-in'))}>Sign in</a></footer>
    <DeveloperExplorerDialog topic={selected} onClose={() => setSelected(null)} />
  </div>
}
