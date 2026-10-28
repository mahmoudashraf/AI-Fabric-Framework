import { useEffect, type ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Button, IconButton, Tooltip, useTheme } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import CodeRounded from '@mui/icons-material/CodeRounded'
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined'
import DnsOutlined from '@mui/icons-material/DnsOutlined'
import AutoAwesomeRounded from '@mui/icons-material/AutoAwesomeRounded'
import ExtensionRounded from '@mui/icons-material/ExtensionRounded'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import HubRounded from '@mui/icons-material/HubRounded'
import LightModeOutlined from '@mui/icons-material/LightModeOutlined'
import LinkRounded from '@mui/icons-material/LinkRounded'
import OpenInFullRounded from '@mui/icons-material/OpenInFullRounded'
import PersonOutlineRounded from '@mui/icons-material/PersonOutlineRounded'
import PersonRounded from '@mui/icons-material/PersonRounded'
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded'
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded'
import PublicRounded from '@mui/icons-material/PublicRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import SendRounded from '@mui/icons-material/SendRounded'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import SmartToyOutlined from '@mui/icons-material/SmartToyOutlined'
import ViewInArRounded from '@mui/icons-material/ViewInArRounded'
import { CustomerConsoleTheme, useCustomerColorPreference } from '../theme/CustomerConsoleTheme'
import './PublicProductsPage.css'

export type PublicProduct = 'platform' | 'engage' | 'desk'

export type PublicProductsPageProps = {
  product?: PublicProduct
  authenticated?: boolean
  consolePath?: string
}

type ProductIcon = typeof ViewInArRounded
type Feature = { icon: ProductIcon; title: string; description: string }

const dealershipDemo = 'https://loomai.pro/demos/dealership-ai'
const dealershipVideo = 'https://youtu.be/rNl_bcMiS4Y'
const assetBase = `${import.meta.env.BASE_URL}products/`

const products: Record<PublicProduct, {
  name: string
  category: string
  icon: ProductIcon
  promise: string
  summary: string
  vision: string
  description: string
  startLabel: string
  features: Feature[]
}> = {
  platform: {
    name: 'LoomAI Platform', category: 'Build & connect', icon: ViewInArRounded,
    promise: 'Connect your systems and deploy your AI.',
    summary: 'Templates, plugins and your own configuration.',
    vision: 'Connect your business systems. Deploy your AI. Keep control.',
    description: 'Start with a template, connect plugins, and configure a dedicated AI deployment for your business.',
    startLabel: 'Get started',
    features: [
      { icon: DnsOutlined, title: 'Connect your data', description: 'Keep indexed knowledge aligned with your systems.' },
      { icon: SettingsOutlined, title: 'Choose the AI behaviour', description: 'Conversations, specialist teams or event-driven plans.' },
      { icon: PublicRounded, title: 'Launch your experience', description: 'Connect Engage or your own application.' },
    ],
  },
  engage: {
    name: 'LoomAI Engage', category: 'Customer experience', icon: ForumOutlined,
    promise: 'Give your customers a branded AI workspace.',
    summary: 'On your website or at its own address.',
    vision: 'An AI workspace your customers can actually use.',
    description: 'Answers, useful actions and human support, branded for your business.',
    startLabel: 'Create a workspace',
    features: [
      { icon: PersonOutlineRounded, title: 'Your brand', description: 'Your name, identity and experience.' },
      { icon: DnsOutlined, title: 'Your connected knowledge', description: "Answers grounded in your deployment's data." },
      { icon: HeadsetMicOutlined, title: 'Your team when needed', description: 'Continue the conversation in LoomAI Desk.' },
    ],
  },
  desk: {
    name: 'LoomAI Desk', category: 'Human support', icon: HeadsetMicOutlined,
    promise: 'Bring your team into AI conversations.',
    summary: 'Human support and action reviews.',
    vision: 'Your people, inside the AI conversation.',
    description: 'Let agents take over when needed, keep the context, and review important actions.',
    startLabel: 'Set up Desk',
    features: [
      { icon: DescriptionOutlined, title: 'Keep the context', description: 'Your team sees the conversation so far.' },
      { icon: PersonOutlineRounded, title: 'Assign your agents', description: 'Give people access to the right deployments.' },
      { icon: ShieldOutlined, title: 'Review actions', description: 'Make authorized decisions in the same service.' },
    ],
  },
}

function PreviewSymbol({ name }: { name: 'box' | 'database' }) {
  const mask = `url(${assetBase}${name}.svg)`
  return <span className={`lp-symbol lp-symbol-${name}`} style={{ maskImage: mask, WebkitMaskImage: mask }} />
}

function ProductMark({ product, small = false }: { product: PublicProduct; small?: boolean }) {
  const Icon = products[product].icon
  return <span className={`lp-mark lp-${product}${small ? ' lp-mark-small' : ''}`}>{product === 'platform' ? <PreviewSymbol name="box" /> : <Icon />}</span>
}

function PreviewFrame({ children, label, className = '' }: { children: ReactNode; label: string; className?: string }) {
  return <div className={`lp-preview ${className}`} role="img" aria-label={`Illustrative preview: ${label}`}>
    <div className="lp-preview-content" aria-hidden="true">{children}</div>
  </div>
}

function PreviewItem({ icon: Icon, title, subtitle, status = false }: {
  icon: ProductIcon; title: string; subtitle?: string; status?: boolean
}) {
  return <div className="lp-preview-item"><Icon /><div><strong>{title}</strong>{subtitle && <span>{subtitle}</span>}</div>
    {status && <CheckCircleOutlineRounded className="lp-connected" />}
  </div>
}

function PlatformPreview({ expanded = false }: { expanded?: boolean }) {
  if (!expanded) return <PreviewFrame label="Your systems connect through plugins to an AI deployment" className="lp-platform-compact">
    <div className="lp-compact-connection">
      <div className="lp-compact-node"><h3>Your systems</h3><div className="lp-system-icons"><PreviewSymbol name="database" /><img src={`${assetBase}google-drive.png`} alt="" width="24" height="24" /><img src={`${assetBase}slack.svg`} alt="" width="24" height="24" /><MoreHorizRounded /></div></div>
      <ArrowForwardRounded className="lp-flow-arrow" />
      <div className="lp-compact-node"><h3>Plugins</h3><ExtensionRounded className="lp-node-icon" /></div>
      <ArrowForwardRounded className="lp-flow-arrow" />
      <div className="lp-compact-node"><h3>AI deployment</h3><div className="lp-deployment-icon"><DnsOutlined className="lp-node-icon" /><AutoAwesomeRounded /></div></div>
    </div>
  </PreviewFrame>
  return <PreviewFrame label="Business systems connect through plugins to a dedicated AI deployment, Engage and Desk"
    className={expanded ? 'lp-platform-expanded' : 'lp-platform-compact'}>
    {expanded && <div className="lp-preview-heading"><ProductMark product="platform" small /><div><strong>Dealership AI</strong><span>Example deployment</span></div><span className="lp-example-badge">Illustrative setup</span></div>}
    <div className="lp-system-flow">
      <div className="lp-flow-column"><h3>Your systems</h3>
        <PreviewItem icon={expanded ? DirectionsCarOutlined : PublicRounded} title={expanded ? 'Stock API' : 'Website'} subtitle={expanded ? 'Live vehicle stock and details' : undefined} status={expanded} />
        <PreviewItem icon={expanded ? DescriptionOutlined : DnsOutlined} title={expanded ? 'Document storage' : 'Database'} subtitle={expanded ? 'Policies and operation guides' : undefined} status={expanded} />
        <PreviewItem icon={expanded ? DnsOutlined : PersonOutlineRounded} title={expanded ? 'Business services' : 'CRM'} subtitle={expanded ? 'Your existing systems' : undefined} status={expanded} />
        {!expanded && <PreviewItem icon={DescriptionOutlined} title="Knowledge base" />}
      </div>
      <ArrowForwardRounded className="lp-flow-arrow" />
      <div className="lp-flow-column lp-flow-enablement"><h3>{expanded ? 'Your AI deployment' : 'Plugins'}</h3>
        <PreviewItem icon={expanded ? CodeRounded : LinkRounded} title={expanded ? 'Templates' : 'API connector'} subtitle={expanded ? 'Start with a deployment template' : undefined} status={expanded} />
        <PreviewItem icon={HubRounded} title={expanded ? 'Data & action plugins' : 'Data sync'} subtitle={expanded ? 'Connect knowledge and actions' : undefined} status={expanded} />
        <PreviewItem icon={expanded ? DescriptionOutlined : SettingsOutlined} title={expanded ? 'Knowledge' : 'Settings'} subtitle={expanded ? 'Indexed data and documents' : undefined} status={expanded} />
      </div>
      <ArrowForwardRounded className="lp-flow-arrow" />
      {expanded ? <>
        <div className="lp-flow-column lp-flow-experience"><h3><ProductMark product="engage" small /> Engage</h3><EngageConversation compact /></div>
        <div className="lp-flow-column lp-flow-support"><h3><ProductMark product="desk" small /> Desk</h3><DeskConversation compact /></div>
      </> : <div className="lp-flow-column lp-flow-runtime"><span>AI deployment</span><ProductMark product="platform" /><strong>LoomAI Platform</strong><span className="lp-ready"><i />Ready</span></div>}
    </div>
  </PreviewFrame>
}

function VehicleResult({ second = false, wide = false }: { second?: boolean; wide?: boolean }) {
  return <div className={`lp-vehicle${wide ? ' lp-vehicle-wide' : ''}`}>
    <img src={`${assetBase}${second ? 'demo-vehicle-02.webp' : 'demo-vehicle-01.webp'}`} alt="" loading="lazy" width="280" height="180" />
    <div><strong>{second ? '2025 Morrow C2' : '2025 Aster E1'}</strong><b>{second ? 'GBP 22,750' : 'GBP 31,950'}</b>
      <span>{second ? '1,980 miles' : '4,850 miles'} &middot; Electric</span></div>
  </div>
}

function PreviewAvatar({ kind }: { kind: 'customer' | 'assistant' | 'agent' }) {
  if (kind === 'agent') return <img className="lp-preview-avatar lp-agent-portrait" src={`${assetBase}demo-support-agent.webp`} alt="" width="32" height="32" loading="lazy" />
  return <span className={`lp-preview-avatar lp-preview-${kind}`}>{kind === 'customer' ? <PersonRounded /> : <SmartToyOutlined />}</span>
}

function EngageConversation({ compact = false, landing = false }: { compact?: boolean; landing?: boolean }) {
  if (landing) return <div className="lp-engage-conversation lp-landing-conversation">
    <div className="lp-mini-message"><PreviewAvatar kind="customer" /><p>Any electric cars under GBP 35,000?</p><span className="lp-message-time">10:24</span></div>
    <div className="lp-mini-message"><PreviewAvatar kind="assistant" /><div className="lp-mini-answer">
      <div className="lp-answer-heading"><p>Here&apos;s a great option within your budget.</p><span className="lp-message-time">10:24</span></div>
      <VehicleResult wide />
    </div></div>
  </div>
  return <div className={`lp-engage-conversation${compact ? ' lp-conversation-compact' : ''}`}>
    {!compact && <div className="lp-mini-app-header"><ProductMark product="engage" small /><strong>Northfield AI</strong><OpenInFullRounded /></div>}
    {!compact && <div className="lp-mini-tools"><span><DirectionsCarOutlined />Browse stock</span><span><DescriptionOutlined />This vehicle</span><span><HeadsetMicOutlined />Human support</span></div>}
    <div className="lp-mini-message"><span className="lp-mini-avatar"><PersonOutlineRounded /></span><p>{compact ? 'Any electric cars under GBP 35,000?' : 'Compare these two electric cars from your current stock.'}</p></div>
    <div className="lp-mini-message"><span className="lp-mini-avatar lp-mini-ai"><ViewInArRounded /></span><div className="lp-mini-answer"><p>{compact ? 'Here is an option from current stock.' : 'Here are the Aster E1 and Morrow C2, using current Northfield stock.'}</p>
      <div className={`lp-vehicle-results${compact ? ' lp-single-result' : ''}`}><VehicleResult wide={compact} />{!compact && <VehicleResult second />}</div>
      {!compact && <p className="lp-mini-source"><DescriptionOutlined />Current stock &middot; Fictional demo data</p>}
    </div></div>
    <div className="lp-mini-input"><span>Ask about your options...</span><SendRounded /></div>
  </div>
}

function EngagePreview({ expanded = false }: { expanded?: boolean }) {
  return <PreviewFrame label={expanded ? 'A branded dealership website alongside an AI workspace with stock comparisons' : 'A branded AI workspace with an answer grounded in current stock'} className={expanded ? 'lp-engage-expanded' : 'lp-engage-compact'}>
    {expanded && <div className="lp-dealer-site">
      <div className="lp-browser-bar"><i /><i /><i /><span>Northfield Motor House &middot; Demo website</span></div>
      <div className="lp-dealer-header"><strong>Northfield Motor House</strong><span>Electric &middot; Used cars</span></div>
      <img className="lp-dealer-photo" src={`${assetBase}demo-vehicle-02.webp`} alt="" width="700" height="350" />
      <div className="lp-dealer-copy"><span>Fictional dealership</span><h3>Find your next electric car.</h3><p>Explore stock, compare your options, and ask your questions.</p></div>
      <div className="lp-dealer-stock"><VehicleResult wide /><VehicleResult second wide /></div>
    </div>}
    <EngageConversation compact={!expanded} landing={!expanded} />
  </PreviewFrame>
}

function DeskConversation({ compact = false, landing = false }: { compact?: boolean; landing?: boolean }) {
  if (landing) return <div className="lp-desk-conversation lp-landing-conversation">
    <div className="lp-desk-message-row"><PreviewAvatar kind="customer" /><p>Can someone help with a test drive?</p><span className="lp-message-time">11:03</span></div>
    <div className="lp-agent-message"><span className="lp-agent-name">Alex &middot; Agent</span><div className="lp-desk-message-row"><span className="lp-message-time">11:04</span><p>Of course. Which day suits you?</p><PreviewAvatar kind="agent" /></div></div>
  </div>
  return <div className={`lp-desk-conversation${compact ? ' lp-conversation-compact' : ''}`}>
    <div className="lp-mini-app-header"><span className="lp-initials">AV</span><div><strong>Anonymous visitor</strong>{!compact && <span className="lp-ready"><i />Human support</span>}</div>{!compact && <span className="lp-return-ai"><RefreshRounded />Return to AI</span>}</div>
    <div className="lp-desk-messages">
      <div className="lp-desk-bubble"><small>Customer</small><p>Can someone help me arrange a test drive?</p></div>
      {!compact && <div className="lp-desk-bubble lp-desk-ai"><small>LoomAI &middot; AI assistant</small><p>I can transfer you to the dealership team.</p></div>}
      <div className="lp-desk-bubble lp-desk-agent"><small>Alex &middot; Agent</small><p>Hello, I can help with your test-drive request. Which day suits you?</p></div>
    </div>
    <div className="lp-mini-input"><span>Reply to the customer...</span><SendRounded /></div>
  </div>
}

function DeskPreview({ expanded = false }: { expanded?: boolean }) {
  return <PreviewFrame label={expanded ? 'An agent inbox with customer and AI messages on the left and the human agent reply on the right' : 'A customer message on the left and a human support agent reply on the right'} className={expanded ? 'lp-desk-expanded' : 'lp-desk-compact'}>
    <div className="lp-desk-sidebar"><strong>Conversations</strong>
      {expanded && <div className="lp-inbox-counts"><span><HeadsetMicOutlined />Waiting for an agent <b>3</b></span><span><PersonOutlineRounded />Assigned to me <b>1</b></span><span><ShieldOutlined />Action reviews <b>2</b></span></div>}
      {[['AV', 'Anonymous visitor', 'Can someone help me arrange...'], ['JL', 'Jamie Lee', 'Charging options for the E1?'], ['PS', 'Priya Sharma', 'Test drive availability'], ['TM', 'Taylor Morgan', 'Exterior colour options']].map(([initials, name, message], index) => <div className={`lp-inbox-row${index === 0 ? ' lp-inbox-selected' : ''}`} key={initials}><span className="lp-initials">{initials}</span><div><strong>{name}</strong><span>{message}</span></div></div>)}
    </div>
    <DeskConversation compact={!expanded} landing={!expanded} />
  </PreviewFrame>
}

function Features({ product }: { product: PublicProduct }) {
  return <div className="lp-features">{products[product].features.map(({ icon: Icon, title, description }) => <div className="lp-feature" key={title}><Icon /><div><h2>{title}</h2><p>{description}</p></div></div>)}</div>
}

function ProductDetail({ product }: { product: PublicProduct }) {
  const details = products[product]
  return <>
    <Button component={RouterLink} to="/products" startIcon={<ArrowBackRounded />} className="lp-back">Back to products</Button>
    <section className={`lp-detail lp-${product}`} aria-labelledby="lp-product-title">
      <div className="lp-detail-intro"><div className="lp-title"><ProductMark product={product} /><h1 id="lp-product-title">{details.name}</h1></div><p className="lp-vision">{details.vision}</p><p className="lp-description">{details.description}</p></div>
      <div className="lp-detail-actions">
        {product !== 'desk' && <Button component="a" href={dealershipDemo} target="_blank" rel="noopener noreferrer" variant="outlined" startIcon={<PlayArrowRounded />} className="lp-demo-button">{product === 'platform' ? 'Try dealership demo' : 'Try live workspace'}</Button>}
        <Button component={RouterLink} to={`/start?product=${product}`} variant="contained" endIcon={<ArrowForwardRounded />} className="lp-product-button">{details.startLabel}</Button>
        {product === 'desk' && <Button component="a" href={dealershipVideo} target="_blank" rel="noopener noreferrer" variant="outlined" startIcon={<PlayArrowRounded />}>Watch dealership demo</Button>}
      </div>
      <figure className="lp-product-figure">
        {product === 'platform' ? <PlatformPreview expanded /> : product === 'engage' ? <EngagePreview expanded /> : <DeskPreview expanded />}
        <figcaption>{product === 'platform' ? 'Your systems, connected to a dedicated AI deployment.' : product === 'engage' ? 'Embed it on your website, or launch it as a standalone workspace.' : 'Your team, with the conversation context already in place.'}</figcaption>
      </figure>
      {product === 'desk' && <div className="lp-handover-flow"><span><ForumOutlined />AI conversation</span><ArrowForwardRounded /><span><PersonOutlineRounded />Human agent</span><ArrowForwardRounded /><span><RefreshRounded />Return to AI</span></div>}
      <Features product={product} />
      <p className="lp-action-note">{product === 'desk' ? 'Use the same account as your LoomAI console. Connect a deployment and assign your agents.' : 'Demo uses fictional stock and a simulated AutoTrader integration.'}</p>
      {product === 'engage' && <p className="lp-action-note">Connect an AI deployment to power your workspace.</p>}
    </section>
  </>
}

function ProductLanding() {
  return <>
    <section className="lp-landing-intro" aria-labelledby="lp-products-title"><h1 id="lp-products-title">LoomAI products</h1><p>Build, engage and support with AI.</p></section>
    <div className="lp-products-grid">{(Object.keys(products) as PublicProduct[]).map(product => {
      const details = products[product]
      return <article className={`lp-product-card lp-${product}`} key={product} aria-labelledby={`lp-card-${product}`}>
        <div className="lp-product-card-intro"><p className="lp-product-category">{details.category}</p>
          <div className="lp-card-title"><ProductMark product={product} /><h2 id={`lp-card-${product}`}>{details.name}</h2></div>
          <p className="lp-promise">{details.promise}</p><p className="lp-summary">{details.summary}</p>
        </div>
        {product === 'platform' ? <PlatformPreview /> : product === 'engage' ? <EngagePreview /> : <DeskPreview />}
        <div className="lp-product-card-content">
          <Button component={RouterLink} to={`/products/${product}`} variant="contained" endIcon={<ArrowForwardRounded />} className="lp-product-button">Explore {product === 'platform' ? 'Platform' : product === 'engage' ? 'Engage' : 'Desk'}</Button>
        </div>
      </article>
    })}</div>
  </>
}

function PublicProductsContent({ product, authenticated = false, consolePath = '/home' }: PublicProductsPageProps) {
  const { palette } = useTheme()
  const { setPreference } = useCustomerColorPreference()
  const selectedProduct = product ?? 'platform'
  const themeLabel = palette.mode === 'dark' ? 'Switch to light mode' : 'Switch to dark mode'
  const authActions = <div className="lp-auth-actions">
    {authenticated ? <Button component={RouterLink} to={consolePath} variant="contained" className="lp-account-button">Open console</Button> : <><Button component={RouterLink} to={`/sign-in?product=${selectedProduct}`}>Sign in</Button><Button component={RouterLink} to={`/sign-up?product=${selectedProduct}`} variant="contained" className="lp-account-button">Create account</Button></>}
  </div>
  const themeToggle = <Tooltip title={themeLabel}><IconButton aria-label={themeLabel} onClick={() => setPreference(palette.mode === 'dark' ? 'light' : 'dark')} size="small">{palette.mode === 'dark' ? <LightModeOutlined fontSize="small" /> : <DarkModeOutlined fontSize="small" />}</IconButton></Tooltip>
  useEffect(() => { window.scrollTo(0, 0) }, [product])
  return <div className="lp-public-products" data-public-products-theme={palette.mode}>
    <header className="lp-header"><a href="https://loomai.pro/" className="lp-brand" aria-label="LoomAI home"><PreviewSymbol name="box" /><span>LoomAI</span></a>{product ? authActions : themeToggle}</header>
    <main className="lp-main">{product ? <ProductDetail product={product} /> : <ProductLanding />}</main>
    <footer className="lp-footer">{product ? <><span>Loom AI Labs</span>{themeToggle}</> : <><span>One account. Your connected AI products.</span>{authActions}</>}</footer>
  </div>
}

/** Public presentation only. The host owns routes, authentication and onboarding. */
export function PublicProductsPage(props: PublicProductsPageProps) {
  return <CustomerConsoleTheme><PublicProductsContent {...props} /></CustomerConsoleTheme>
}
