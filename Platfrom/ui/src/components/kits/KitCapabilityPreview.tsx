import { useId, useState } from 'react'
import { Button, Dialog, IconButton, ToggleButton, ToggleButtonGroup, Tooltip, useMediaQuery, useTheme } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import AttachFileRounded from '@mui/icons-material/AttachFileRounded'
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import CheckRounded from '@mui/icons-material/CheckRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import LocalShippingOutlined from '@mui/icons-material/LocalShippingOutlined'
import MenuBookOutlined from '@mui/icons-material/MenuBookOutlined'
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded'
import OpenInFullRounded from '@mui/icons-material/OpenInFullRounded'
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined'
import PersonRounded from '@mui/icons-material/PersonRounded'
import SearchRounded from '@mui/icons-material/SearchRounded'
import SendRounded from '@mui/icons-material/SendRounded'
import ShoppingBagOutlined from '@mui/icons-material/ShoppingBagOutlined'
import HandshakeOutlined from '@mui/icons-material/HandshakeOutlined'
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined'
import ViewListOutlined from '@mui/icons-material/ViewListOutlined'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import { KitDeskMark, KitMark, kitAsset } from './KitIdentity'
import { kitPreviewContent, kitPreviewFormats, type KitPreviewContent, type KitPreviewContext, type KitPreviewFormat, type KitPreviewKind } from './kitPreviewContent'
import './KitCapabilityPreview.css'
import { SupportKitExperiencePreview, SupportKitIllustration } from './SupportKitPreview'
import { KitDeliveryScene } from './KitDetailScenes'
import WebAssetOutlined from '@mui/icons-material/WebAssetOutlined'
import DesktopWindowsOutlined from '@mui/icons-material/DesktopWindowsOutlined'
import OpenInBrowserOutlined from '@mui/icons-material/OpenInBrowserOutlined'

export function KitScope({ kind }: { kind: KitPreviewKind }) {
  if (kind === 'support') return <><span className="ek-scope-badge">Built for support</span><div className="ek-scope-line"><p>Your guidance. Linked requests. Optional human support.</p></div></>
  const content = kitPreviewContent(kind)
  return <><span className="ek-scope-badge">{content.badge}</span><div className="ek-scope-line"><p>{content.scope}</p>
    <span aria-hidden="true">{content.dealership ? <><DirectionsCarOutlined /><ViewListOutlined /><CalendarMonthOutlined /></>
      : <><ShoppingBagOutlined /><HandshakeOutlined /><MenuBookOutlined /></>}</span></div></>
}

export function KitIncludedProducts() {
  return <section className="ek-included-products" aria-label="Products included in this experience"><h3>Powered by</h3><div>
    <div><KitMark product="platform" /><span><strong>LoomAI Platform</strong><small>Connect your business</small></span></div><b aria-hidden="true">+</b>
    <div><KitMark product="engage" /><span><strong>LoomAI Engage</strong><small>Brand the experience</small></span></div><b aria-hidden="true">+</b>
    <div><KitDeskMark /><span><strong>LoomAI Desk <em>Optional</em></strong><small>Bring your team</small></span></div>
  </div></section>
}

function ProductTiles({ content, select, selected }: { content: KitPreviewContent; select?: (index: number) => void; selected?: number }) {
  return <div className="ek-cap-products">{content.products.map((product, index) => {
    const body = <><img src={kitAsset(product.image)} alt={select ? product.alt : ''} width="480" height="360" loading="lazy" />
      <span><strong>{product.name}</strong><small>{product.price}</small><ArrowForwardRounded aria-hidden="true" /></span></>
    return select ? <button key={product.name} type="button" onClick={() => select(index)} aria-label={`View ${product.name}`} aria-pressed={selected === index}>{body}</button>
      : <div key={product.name}>{body}</div>
  })}</div>
}

function Conversation({ content, select, selected, onContext, onHuman, human = false }: {
  content: KitPreviewContent; select?: (index: number) => void; selected?: number;
  onContext?: (context: KitPreviewContext) => void; onHuman?: () => void; human?: boolean
}) {
  const NextIcon = content.dealership ? CalendarMonthOutlined : LocalShippingOutlined
  const agentRow = <><img src={kitAsset('demo-support-agent.webp')} alt={onHuman ? 'Fictional example support agent' : ''} width="40" height="40" loading="lazy" />
    <span><strong>{human ? 'Alex · Example support agent' : content.agentLabel}</strong><small>{human ? content.agentMessage : 'Optional with LoomAI Desk'}</small></span>
    {!human && <ArrowForwardRounded aria-hidden="true" />}</>
  return <div className="ek-cap-conversation">
    <div className="ek-cap-query"><PersonRounded aria-hidden="true" /><p>{content.query}</p></div>
    <div className="ek-cap-answer"><KitMark product="engage" /><p>{content.answer}</p></div>
    <ProductTiles content={content} select={select} selected={selected} />
    <div className="ek-cap-actions">{onContext ? <>
      <Button variant="outlined" startIcon={<CompareArrowsRounded />} onClick={() => onContext('compare')}>Compare{content.dealership ? ' cars' : ''}</Button>
      <Button variant="outlined" startIcon={<NextIcon />} onClick={() => onContext('next-step')}>{content.nextAction}</Button>
    </> : <><span><CompareArrowsRounded />Compare{content.dealership ? ' cars' : ''}</span><span><NextIcon />{content.nextAction}</span></>}</div>
    {human && <p className="ek-cap-joined"><HeadsetMicOutlined />Alex joined this example conversation</p>}
    {onHuman ? <button type="button" className="ek-cap-agent" onClick={onHuman} aria-label={human ? 'Return to AI preview' : 'Preview human handover'}>{agentRow}</button>
      : <div className="ek-cap-agent">{agentRow}</div>}
    <div className="ek-cap-composer" aria-hidden="true"><AttachFileRounded /><span>{human ? 'Reply to Alex...' : content.dealership ? 'Ask about these cars...' : 'Ask about these options...'}</span><SendRounded /></div>
  </div>
}

export function KitCapabilityIllustration({ kind }: { kind: KitPreviewKind }) {
  if (kind === 'support') return <SupportKitIllustration />
  const content = kitPreviewContent(kind)
  return <figure className="ek-cap-illustration" role="img" aria-label={`Illustrative ${content.brand}: product discovery, comparisons, ${content.nextAction.toLowerCase()} and optional human support. Fictional example, not live AI or an assigned agent.`}>
    <div aria-hidden="true"><div className="ek-cap-heading"><KitMark product="engage" /><strong>{content.brand}</strong><span>{content.disclosure}</span><MoreHorizRounded /></div>
      <Conversation content={content} /></div>
  </figure>
}

function FormatMiniature({ content, format }: { content: KitPreviewContent; format: KitPreviewFormat }) {
  return <span className={`ek-format-screen ek-format-${format}`} aria-hidden="true">
    <span className="ek-mini-browser"><i /><i /><i />{format === 'standalone' && <span>your-brand.example</span>}</span>
    {format === 'website' ? <span className="ek-mini-site"><strong>{content.website}</strong><img src={kitAsset(content.products[0].image)} alt="" width="180" height="110" loading="lazy" /><span className="ek-mini-widget"><ForumOutlined /></span></span>
      : <span className="ek-mini-workspace"><span className="ek-mini-brand"><ForumOutlined />{content.brand}</span><span className="ek-mini-body"><i /><span>
        {format === 'standalone' && <><i /><i /></>}<span className="ek-mini-products">{content.products.slice(0, format === 'max' ? 2 : 1).map(product => <img key={product.name} src={kitAsset(product.image)} alt="" width="90" height="70" loading="lazy" />)}</span>
      </span></span><span className="ek-mini-input" /></span>}
  </span>
}

function PreviewContext({ content, context, selected }: { content: KitPreviewContent; context: KitPreviewContext; selected: number }) {
  const product = content.products[selected]
  return <aside className="ek-demo-context" aria-live="polite">
    {context === 'compare' ? <><h3>Compare your options</h3><div className="ek-demo-compare">{content.products.map(item => <div key={item.name}>
      <img src={kitAsset(item.image)} alt={item.alt} width="480" height="360" /><strong>{item.name}</strong><span>{item.price}</span><p>{item.comparison}</p>
    </div>)}</div><p className="ek-demo-note">Fictional examples for this preview.</p></>
      : context === 'next-step' ? <><h3>{content.dealership ? 'Test-drive enquiry' : 'Example delivery guide'}</h3>
        {content.dealership ? <><img className="ek-demo-context-photo" src={kitAsset(product.image)} alt={product.alt} width="480" height="360" /><strong>{product.name}</strong>
          <p>Your team can review the vehicle, preferred time and contact details before confirming a test drive.</p><p className="ek-demo-note">Example only. No enquiry has been sent.</p></>
          : <><LocalShippingOutlined className="ek-demo-delivery-icon" /><p>Explore delivery options alongside the products you are considering.</p><dl><div><dt>Standard delivery</dt><dd>3-5 working days</dd></div><div><dt>Collection</dt><dd>Arrange with the team</dd></div></dl>
            <p className="ek-demo-note">Fictional delivery policy. Your own approved information appears here.</p></>}</>
        : <><h3>A closer look</h3><img className="ek-demo-context-photo" src={kitAsset(product.image)} alt={product.alt} width="480" height="360" />
          <div className="ek-demo-product-heading"><strong>{product.name}</strong><span>{product.price}</span></div>
          <ul>{product.details.map(detail => <li key={detail}><CheckRounded />{detail}</li>)}</ul><p className="ek-demo-note">Example content, not a live product offer.</p></>}
  </aside>
}

function PreviewStage({ content, format }: { content: KitPreviewContent; format: KitPreviewFormat }) {
  const [selected, setSelected] = useState(0)
  const [context, setContext] = useState<KitPreviewContext>('details')
  const [human, setHuman] = useState(false)
  return <div className={`ek-preview-stage ek-stage-${format}${content.dealership ? ' ek-stage-dealership' : ''}`}>
    {format === 'website' && <div className="ek-demo-website"><div>{content.website}<span>Collection &nbsp; Our story</span></div><h3>{content.headline}</h3>
      <img src={kitAsset(content.products[0].image)} alt={content.products[0].alt} width="720" height="540" /><p>{content.dealership ? 'Discover your next car.' : 'Thoughtful pieces for everyday living.'}</p></div>}
    <div className="ek-demo-window">
      {format === 'standalone' && <div className="ek-demo-address"><i /><i /><i /><span>ai.your-business.example</span></div>}
      <div className="ek-cap-heading"><KitMark product="engage" /><div><strong>{content.brand}</strong><small>{content.dealership ? 'Your dealership, within reach.' : 'Find your space. Make it yours.'}</small></div><span>{content.disclosure}</span></div>
      <nav className="ek-demo-tools" aria-label="Example workspace tools"><Button startIcon={<SearchRounded />} onClick={() => setContext('details')}>Browse</Button>
        <Button startIcon={<CompareArrowsRounded />} onClick={() => setContext('compare')}>Compare</Button>
        <Button startIcon={content.dealership ? <CalendarMonthOutlined /> : <LocalShippingOutlined />} onClick={() => setContext('next-step')}>{content.nextAction}</Button>
        <Button startIcon={<HeadsetMicOutlined />} onClick={() => setHuman(value => !value)}>{human ? 'AI preview' : 'Ask our team'}</Button></nav>
      <div className="ek-demo-body"><Conversation content={content} selected={selected} select={index => { setSelected(index); setContext('details') }}
        onContext={setContext} onHuman={() => setHuman(value => !value)} human={human} />
        <PreviewContext content={content} context={context} selected={selected} /></div>
    </div>
  </div>
}

export function KitExperiencePreview({ kind, name, children, compact = false, detail = false }: { kind: KitPreviewKind; name: string; children?: React.ReactNode; compact?: boolean; detail?: boolean }) {
  return kind === 'support' ? <>{!compact && !detail && <KitIncludedProducts />}<SupportKitExperiencePreview name={name} compact={compact} detail={detail}>{children}</SupportKitExperiencePreview></>
    : <ShoppingKitExperiencePreview kind={kind} name={name} compact={compact} detail={detail}>{children}</ShoppingKitExperiencePreview>
}

function ShoppingKitExperiencePreview({ kind, name, children, compact, detail }: { kind: Exclude<KitPreviewKind, 'support'>; name: string; children?: React.ReactNode; compact: boolean; detail: boolean }) {
  const [format, setFormat] = useState<KitPreviewFormat>('max')
  const [open, setOpen] = useState(false)
  const { palette } = useTheme()
  const narrow = useMediaQuery('(max-width:650px)')
  const titleId = useId()
  const content = kitPreviewContent(kind)
  const show = (next: KitPreviewFormat) => { setFormat(next); setOpen(true) }
  return <>
    {!compact && <section className={`ek-experience-formats${detail ? ' ek-detail-formats' : ''}`} aria-label={`Ways to use ${name}`}>
      {!detail && <div><h3>Three ways customers can use it</h3><small>Choose one or combine them.</small></div>}
      <div className="ek-format-grid">{kitPreviewFormats.map(item => <button key={item.id} type="button" onClick={() => show(item.id)}
        aria-label={`Preview ${item.label} for ${name}`} className={item.id === 'max' ? 'ek-format-featured' : ''}>
        {detail ? <><span className="ek-detail-format-label">{item.id === 'website' ? <WebAssetOutlined /> : item.id === 'max' ? <DesktopWindowsOutlined /> : <OpenInBrowserOutlined />}{item.label}</span>
          <KitDeliveryScene kind={kind} format={item.id} /><strong>{item.id === 'website' ? 'Help while they browse' : item.id === 'max' ? 'Explore and compare in depth' : 'Give customers their own space'}</strong><small>{item.description}<ArrowForwardRounded /></small></>
          : <><FormatMiniature content={content} format={item.id} /><strong>{item.label}</strong></>}</button>)}</div>
    </section>}
    {!compact && !detail && <KitIncludedProducts />}
    <div className="ek-cap-commands">{children}<Button variant={compact ? 'text' : 'outlined'} startIcon={compact ? <VisibilityOutlined /> : <OpenInFullRounded />} onClick={() => show('max')}>{compact ? 'Preview workspace' : 'Preview experience'}</Button></div>
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="xl" fullScreen={narrow} aria-labelledby={titleId}
      PaperProps={{ className: 'ek-page ek-experience-dialog', 'data-kit-theme': palette.mode } as React.HTMLAttributes<HTMLDivElement>}>
      <div className="ek-experience-dialog-header"><div><KitMark product="platform" /><h2 id={titleId}>Workspace preview</h2><span>Illustrative experience</span></div>
        <Tooltip title="Close preview"><IconButton aria-label="Close preview" onClick={() => setOpen(false)}><CloseRounded /></IconButton></Tooltip></div>
      <div className="ek-experience-format-picker"><ToggleButtonGroup exclusive value={format} onChange={(_, value: KitPreviewFormat | null) => { if (value) setFormat(value) }} aria-label="Workspace preview format">
        {kitPreviewFormats.map(item => <ToggleButton value={item.id} key={item.id}>{item.label}</ToggleButton>)}</ToggleButtonGroup>
        <p>{kitPreviewFormats.find(item => item.id === format)?.description}</p></div>
      <div className="ek-experience-dialog-body"><PreviewStage content={content} format={format} /></div>
      <p className="ek-experience-dialog-footer">Example content only. No live AI or requests. Human support is optional with LoomAI Desk.</p>
    </Dialog>
  </>
}
