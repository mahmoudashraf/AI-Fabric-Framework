import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import BoltOutlined from '@mui/icons-material/BoltOutlined'
import CheckRounded from '@mui/icons-material/CheckRounded'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import DnsOutlined from '@mui/icons-material/DnsOutlined'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import InboxOutlined from '@mui/icons-material/InboxOutlined'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import PersonOutlineRounded from '@mui/icons-material/PersonOutlineRounded'
import SendRounded from '@mui/icons-material/SendRounded'
import WebOutlined from '@mui/icons-material/WebOutlined'
import { DEVELOPER_KITS, developerConsoleHref, developerKitSetupPath, type DeveloperKitCode } from './developerEntry'
import '../pages/DeveloperLandingPage.css'

const asset = `${import.meta.env.BASE_URL}products/`

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

export function DeveloperStarterCard({ code, support = false }: { code: DeveloperKitCode; support?: boolean }) {
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


export function DeveloperStarterCards({ availableCodes = DEVELOPER_KITS }: { availableCodes?: readonly string[] }) {
  return <div className="dev-starter-catalogue" data-developer-starters><div className="dev-starter-grid">
    {DEVELOPER_KITS.filter(code => availableCodes.includes(code)).map(code =>
      <DeveloperStarterCard key={code} code={code} support={code === 'ai-support-experience'} />)}
  </div></div>
}
