import { useEffect, useState } from 'react'
import { Dialog, DialogActions, DialogContent, DialogTitle, IconButton } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import ArrowOutwardRounded from '@mui/icons-material/ArrowOutwardRounded'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import BoltOutlined from '@mui/icons-material/BoltOutlined'
import CheckRounded from '@mui/icons-material/CheckRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import CodeRounded from '@mui/icons-material/CodeRounded'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import DnsOutlined from '@mui/icons-material/DnsOutlined'
import ExtensionOutlined from '@mui/icons-material/ExtensionOutlined'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import HubOutlined from '@mui/icons-material/HubOutlined'
import InboxOutlined from '@mui/icons-material/InboxOutlined'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import MemoryOutlined from '@mui/icons-material/MemoryOutlined'
import PersonOutlineRounded from '@mui/icons-material/PersonOutlineRounded'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import RedeemOutlined from '@mui/icons-material/RedeemOutlined'
import SchoolOutlined from '@mui/icons-material/SchoolOutlined'
import SearchOutlined from '@mui/icons-material/SearchOutlined'
import SendRounded from '@mui/icons-material/SendRounded'
import SettingsInputComponentOutlined from '@mui/icons-material/SettingsInputComponentOutlined'
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined'
import TuneOutlined from '@mui/icons-material/TuneOutlined'
import WebOutlined from '@mui/icons-material/WebOutlined'
import { developerConsoleHref, developerKitSetupPath, type DeveloperKitCode } from '../developer/developerEntry'
import './DeveloperLandingPage.css'

const asset = `${import.meta.env.BASE_URL}products/`
const capabilities = [
  { id: 'data', name: 'Your data', summary: 'Documents · Live sync', icon: DnsOutlined,
    detail: 'Connect approved documents or live records from your systems. Reuse the existing document storage, source approval and indexing tools.' },
  { id: 'integrations', name: 'Integrations', summary: 'Plugins · Your APIs', icon: ExtensionOutlined,
    detail: 'Install a reusable plugin or define an HTTP data source. Each Brain uses its own authorized connections and secret references.' },
  { id: 'retrieval', name: 'Retrieval', summary: 'Vector search · Grounded RAG', icon: SearchOutlined,
    detail: 'Choose search and embedding services, connect knowledge sources, and inspect the evidence used for an answer.' },
  { id: 'actions', name: 'Actions', summary: 'Read · Write · Confirm', icon: BoltOutlined,
    detail: 'Connect your existing APIs through declared actions. Your permissions, trusted targets and configured confirmations still govern execution.' },
  { id: 'models', name: 'AI models', summary: 'Your choice of provider', icon: MemoryOutlined,
    detail: 'Configure an available model provider through the existing AI models editor. Provider credentials, funding and resource limits still apply.' },
  { id: 'behaviour', name: 'Behaviour', summary: 'Prompts · Response style', icon: TuneOutlined,
    detail: 'Adapt tone, answer breadth, response length and next steps through managed behaviour profiles, with advanced prompt editing when needed.' },
  { id: 'specialists', name: 'Specialist teams', summary: 'Sequential · Parallel plans', icon: HubOutlined,
    detail: 'Compose supported specialist and execution-plan capabilities. Their configuration and runtime readiness must be qualified before use; they are not automatically enabled by a starter Kit.' },
  { id: 'human', name: 'Human in the loop', summary: 'Handover · Action reviews', icon: HeadsetMicOutlined,
    detail: 'Connect LoomAI Desk, assign authorized agents, and let conversations continue with a person. Important actions can use the existing human-review workflow.' },
] as const

function ProductLine({ support = false }: { support?: boolean }) {
  return <div className="dev-product-line"><LayersOutlined /><span>Platform</span><b>+</b><ForumOutlined /><span>Engage</span>{support && <><b>+</b><HeadsetMicOutlined /><span>Desk</span></>}</div>
}

function ChatPreview({ support = false }: { support?: boolean }) {
  return <div className={`dev-app-preview${support ? ' dev-support-preview' : ''}`} aria-label={support ? 'Illustrative AI and human support workspace' : 'Illustrative customer AI workspace'}>
    <div className="dev-app-bar"><LayersOutlined /><strong>{support ? 'My project Support' : 'My project'}</strong><span>{support ? 'Support' : 'AI workspace'}</span><i /></div>
    <div className="dev-preview-body">
      <div className="dev-preview-chat">
        <div className="dev-message"><span className="dev-avatar"><PersonOutlineRounded /></span><p>{support ? 'Can someone help with my setup?' : 'Which option fits my workspace?'}</p></div>
        <div className="dev-message"><span className="dev-avatar dev-avatar-ai"><AutoAwesomeOutlined /></span><div className="dev-answer">
          <p>{support ? 'Here is the setup guide. Your team can help too.' : 'Here are two options from your catalogue.'}</p>
          {support ? <div className="dev-guide"><DescriptionOutlined /><span><strong>Setup guide</strong><small>Your approved knowledge</small></span><CheckRounded /></div>
            : <div className="dev-result-row"><div><img src={`${asset}example-forma-chair.webp`} alt="Example workspace chair" loading="lazy" width="88" height="74" /><span><strong>Forma chair</strong><small>Compact & adaptable</small></span></div><div><DescriptionOutlined /><span><strong>Product guide</strong><small>Compare the details</small></span></div></div>}
          <div className="dev-preview-source">{support ? <><HeadsetMicOutlined />Connected to your team</> : <><DescriptionOutlined />Source: Your knowledge</>}</div>
        </div></div>
        <div className="dev-preview-composer"><AutoAwesomeOutlined /><span>{support ? 'Continue the conversation...' : 'Ask a follow-up...'}</span><SendRounded /></div>
      </div>
      {support && <div className="dev-preview-inbox"><strong>Team inbox</strong><span className="dev-inbox-selected"><InboxOutlined />Setup question</span><span><DescriptionOutlined />Billing help</span><div className="dev-agent"><img src={`${asset}demo-support-agent.webp`} width="30" height="30" alt="Illustrative support agent" loading="lazy" /><div><strong>Alex · Team</strong><p>Happy to help. Let&apos;s get your setup connected.</p></div></div></div>}
    </div>
  </div>
}

function StarterCard({ code, support = false }: { code: DeveloperKitCode; support?: boolean }) {
  const facts = support ? [[DescriptionOutlined, 'Approved knowledge'], [InboxOutlined, 'Requests & follow-ups'], [HeadsetMicOutlined, 'Human handover']] as const
    : [[DnsOutlined, 'Your data & APIs'], [BoltOutlined, 'Answers & actions'], [WebOutlined, 'Website or standalone']] as const
  return <article className={`dev-starter${support ? ' dev-starter-support' : ''}`}>
    <div className="dev-starter-eyebrow"><AutoAwesomeOutlined /><span>{support ? 'FOR YOUR SUPPORT TEAM' : 'FOR YOUR CUSTOMERS'}</span><span className="dev-staging-badge">Staging only</span></div>
    <h3>{support ? 'Support Team Brain' : 'Customer Experience Brain'}</h3>
    <p className="dev-starter-promise">{support ? 'Help users with AI, with your team one step away.' : 'Turn your app into an AI-powered experience.'}</p>
    <ChatPreview support={support} />
    <ul className="dev-starter-facts">{facts.map(([Icon, name]) => <li key={name}><Icon /><span>{name}</span></li>)}</ul>
    <a className="dev-button dev-starter-button" href={developerConsoleHref(developerKitSetupPath(code))}>{support ? 'Build a support team' : 'Build a customer experience'}<ArrowForwardRounded /></a>
    <ProductLine support={support} />
  </article>
}

export function DeveloperLandingPage({ authenticated = false }: { authenticated?: boolean }) {
  const [selected, setSelected] = useState<typeof capabilities[number] | null>(null)
  useEffect(() => {
    const title = document.title
    document.title = 'Smart Brain for your app | LoomAI Developers'
    return () => { document.title = title }
  }, [])
  return <div className="dev-landing" data-developer-landing>
    <header className="dev-header"><span className="dev-brand"><img src={`${asset}box.svg`} width="28" height="28" alt="" /><strong>LoomAI</strong></span><span className="dev-for-developers">FOR DEVELOPERS</span>
      <nav aria-label="Developer account"><a href={developerConsoleHref('/sign-in')}>Sign in</a><a className="dev-button dev-console-button" href={developerConsoleHref(authenticated ? '/home' : '/sign-in')}>Open Console<ArrowOutwardRounded /></a></nav></header>
    <main>
      <section className="dev-hero" aria-labelledby="dev-title"><p className="dev-eyebrow">YOUR APP. YOUR AI.</p><h1 id="dev-title">Smart Brain for your app.</h1><p className="dev-hero-copy">Connect your data, choose how it thinks, and give your project useful AI.</p>
        <div className="dev-hero-actions"><a href="#developer-brains" className="dev-button">Create a Smart Brain<ArrowForwardRounded /></a><a href="https://loomai.pro/demos/dealership-ai" target="_blank" rel="noopener noreferrer" className="dev-demo"><PlayCircleOutlineRounded />Explore a demo</a></div>
        <div className="dev-brain-map">
          <svg className="dev-connections" viewBox="0 0 1200 430" preserveAspectRatio="none" aria-hidden="true"><g>{[68,166,264,362].map((y, index) => <g key={y}><path d={`M305 ${y} C400 ${y}, 410 ${140 + index * 34}, 490 ${140 + index * 34}`} /><path d={`M895 ${y} C800 ${y}, 790 ${140 + index * 34}, 710 ${140 + index * 34}`} /><circle cx="305" cy={y} r="3" /><circle cx="895" cy={y} r="3" /></g>)}</g></svg>
          <div className="dev-capabilities dev-capabilities-left">{capabilities.slice(0, 4).map(item => <button key={item.id} className="dev-capability" onClick={() => setSelected(item)} aria-haspopup="dialog"><item.icon /><span><strong>{item.name}</strong><small>{item.summary}</small></span><ArrowOutwardRounded className="dev-capability-open" /></button>)}</div>
          <div className="dev-brain"><img src={`${import.meta.env.BASE_URL}developer/smart-brain.png`} alt="Sculptural silver AI brain with teal neural connections on a compute base" width="1280" height="1280" fetchPriority="high" /><strong>My project brain</strong><span>Configured in LoomAI</span></div>
          <div className="dev-capabilities dev-capabilities-right">{capabilities.slice(4).map(item => <button key={item.id} className={`dev-capability${item.id === 'human' ? ' dev-capability-human' : ''}`} onClick={() => setSelected(item)} aria-haspopup="dialog"><item.icon /><span><strong>{item.name}</strong><small>{item.summary}</small></span><ArrowOutwardRounded className="dev-capability-open" /></button>)}</div>
        </div>
        <div className="dev-app-connection"><div className="dev-endpoint"><CodeRounded /><strong>Your app or side project</strong><code>POST {'{your-brain-url}'}/api/chat/me/query</code></div><div className="dev-app-targets"><span><WebOutlined />Web app</span><span><SmartphoneOutlined />Mobile app</span><span><DnsOutlined />Backend</span></div></div>
      </section>
      <section className="dev-build-steps" aria-labelledby="dev-steps-title"><h2 id="dev-steps-title">Start small. Add capabilities as you build.</h2><ol>{[[LayersOutlined, 'Choose a template'], [SettingsInputComponentOutlined, 'Connect your tools'], [PlayCircleOutlineRounded, 'Test your Brain'], [CodeRounded, 'Use it in your app']].map(([Icon, label], index) => <li key={String(label)}><b>{index + 1}</b><>{typeof Icon !== 'string' && <Icon />}</><span>{String(label)}</span>{index < 3 && <ArrowForwardRounded className="dev-step-arrow" />}</li>)}</ol></section>
      <section className="dev-programme" aria-label="Developer programme"><p className="dev-eyebrow">DEVELOPER PROGRAMME</p><div className="dev-offer"><div><RedeemOutlined /><strong>Free for 1 year</strong><span className="dev-staging-badge">Developer staging</span></div><div><SchoolOutlined /><strong>Training by LoomAI included</strong></div></div><p>Build your smart app, learn the tools, and make it your own.</p><small>Developer platform access for one year. Staging only. Existing model-usage and infrastructure allowances apply.</small></section>
      <section className="dev-starters" id="developer-brains" aria-labelledby="dev-starters-title"><h2 id="dev-starters-title">What will you build first?</h2><p>Two ready-to-adapt starting points. The same configurable Smart Brain.</p><div className="dev-starter-grid"><StarterCard code="ai-customer-experience" /><StarterCard code="ai-support-experience" support /></div><p className="dev-closing">Connect your own systems. Keep control of your project.</p><a className="dev-training" href={developerConsoleHref('/support?new=1')}><SchoolOutlined />Ask LoomAI about developer training<ArrowForwardRounded /></a></section>
    </main>
    <footer className="dev-footer"><span className="dev-brand"><img src={`${asset}box.svg`} width="23" height="23" alt="" /><strong>LoomAI</strong></span><span>For developers</span><a href="https://loomai.pro/products/loomai-platform">Explore the platform</a><a href={developerConsoleHref('/sign-in')}>Sign in</a></footer>
    <Dialog open={Boolean(selected)} onClose={() => setSelected(null)} maxWidth="sm" fullWidth className="dev-capability-dialog"><DialogTitle>{selected?.name}<IconButton aria-label="Close capability preview" onClick={() => setSelected(null)} sx={{ position: 'absolute', top: 10, right: 10 }}><CloseRounded /></IconButton></DialogTitle><DialogContent><p>{selected?.detail}</p></DialogContent><DialogActions><a className="dev-button" href="#developer-brains" onClick={() => setSelected(null)}>Choose your starting point<ArrowForwardRounded /></a></DialogActions></Dialog>
  </div>
}
