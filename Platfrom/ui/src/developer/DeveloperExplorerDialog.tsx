import { useEffect, useState, type ElementType } from 'react'
import { Dialog, DialogActions, DialogContent, DialogTitle, IconButton, ToggleButton, ToggleButtonGroup, Tooltip } from '@mui/material'
import ApiOutlined from '@mui/icons-material/ApiOutlined'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import AltRouteRounded from '@mui/icons-material/AltRouteRounded'
import BoltOutlined from '@mui/icons-material/BoltOutlined'
import BubbleChartOutlined from '@mui/icons-material/BubbleChartOutlined'
import CheckRounded from '@mui/icons-material/CheckRounded'
import CloudOutlined from '@mui/icons-material/CloudOutlined'
import CloseRounded from '@mui/icons-material/CloseRounded'
import CodeRounded from '@mui/icons-material/CodeRounded'
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import DnsOutlined from '@mui/icons-material/DnsOutlined'
import EditNoteOutlined from '@mui/icons-material/EditNoteOutlined'
import ExtensionOutlined from '@mui/icons-material/ExtensionOutlined'
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import ForestOutlined from '@mui/icons-material/ForestOutlined'
import FormatAlignLeftRounded from '@mui/icons-material/FormatAlignLeftRounded'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import HubOutlined from '@mui/icons-material/HubOutlined'
import LayersOutlined from '@mui/icons-material/LayersOutlined'
import MemoryOutlined from '@mui/icons-material/MemoryOutlined'
import OpenInFullRounded from '@mui/icons-material/OpenInFullRounded'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import RecordVoiceOverOutlined from '@mui/icons-material/RecordVoiceOverOutlined'
import SearchOutlined from '@mui/icons-material/SearchOutlined'
import SettingsInputComponentOutlined from '@mui/icons-material/SettingsInputComponentOutlined'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import SmartphoneOutlined from '@mui/icons-material/SmartphoneOutlined'
import SyncOutlined from '@mui/icons-material/SyncOutlined'
import TuneOutlined from '@mui/icons-material/TuneOutlined'
import WebOutlined from '@mui/icons-material/WebOutlined'
import { developerConsoleHref, developerKitSetupPath } from './developerEntry'

export const developerCapabilities = [
  { id: 'data', name: 'Your data', summary: 'Documents · Live sync', icon: DnsOutlined },
  { id: 'integrations', name: 'Integrations', summary: 'Plugins · Your APIs', icon: ExtensionOutlined },
  { id: 'retrieval', name: 'Retrieval', summary: 'Vector search · Grounded RAG', icon: SearchOutlined },
  { id: 'actions', name: 'Actions', summary: 'Read · Write · Confirm', icon: BoltOutlined },
  { id: 'models', name: 'AI models', summary: 'Your choice of provider', icon: MemoryOutlined },
  { id: 'behaviour', name: 'Behaviour', summary: 'Prompts · Response style', icon: TuneOutlined },
  { id: 'specialists', name: 'Specialist teams', summary: 'Sequential · Parallel plans', icon: HubOutlined },
  { id: 'human', name: 'Human in the loop', summary: 'Handover · Action reviews', icon: HeadsetMicOutlined },
] as const
export const developerChannels = [
  { id: 'web', name: 'Web app', icon: WebOutlined },
  { id: 'mobile', name: 'Mobile app', icon: SmartphoneOutlined },
  { id: 'backend', name: 'Backend', icon: DnsOutlined },
] as const
export const developerSteps = [
  { id: 'templates', name: 'Choose a template', icon: LayersOutlined },
  { id: 'connect', name: 'Connect your tools', icon: SettingsInputComponentOutlined },
  { id: 'test', name: 'Test your Brain', icon: PlayCircleOutlineRounded },
  { id: 'use', name: 'Use it in your app', icon: CodeRounded },
] as const
export type DeveloperTopic = typeof developerCapabilities[number]['id'] | typeof developerChannels[number]['id'] | typeof developerSteps[number]['id']
type VisualOption = { name: string; description: string; icon?: ElementType; logo?: string; monochrome?: boolean }
const providerAsset = `${import.meta.env.BASE_URL}developer/providers/`
const modelProviders: VisualOption[] = [
  { name: 'OpenAI', description: 'GPT models', logo: 'openai.svg', monochrome: true },
  { name: 'Anthropic', description: 'Claude models', logo: 'claude-color.svg' },
  { name: 'Google Gemini', description: 'Gemini models', logo: 'gemini-color.svg' },
  { name: 'Azure OpenAI', description: 'Your Azure deployments', logo: 'azure-color.svg' },
  { name: 'Cohere', description: 'Command models', logo: 'cohere-color.svg' },
]
const embeddingProviders: VisualOption[] = [modelProviders[0], modelProviders[3], modelProviders[4], modelProviders[2],
  { name: 'ONNX', description: 'Local embeddings', logo: 'onnx.svg', monochrome: true }]
const searchEngines: VisualOption[] = [
  { name: 'Milvus', description: 'External vector search', logo: 'milvus.svg', monochrome: true },
  { name: 'Qdrant', description: 'External vector search', logo: 'qdrant.svg', monochrome: true },
  { name: 'Pinecone', description: 'External vector search', icon: ForestOutlined },
  { name: 'Weaviate', description: 'External vector search', icon: BubbleChartOutlined },
  { name: 'Lucene', description: 'Local search index', logo: 'apachelucene.svg', monochrome: true },
  { name: 'Memory', description: 'Temporary in-memory search', icon: MemoryOutlined },
]
const connections: VisualOption[] = [
  { name: 'S3-compatible storage', description: 'Your approved documents', icon: CloudOutlined },
  { name: 'HTTP JSON APIs', description: 'Read live data from your systems', icon: ApiOutlined },
  { name: 'Action endpoints', description: 'Your application executes the work', icon: BoltOutlined },
  { name: 'LoomAI Desk', description: 'Connect your human support team', icon: HeadsetMicOutlined },
]
const content: Record<DeveloperTopic, { title: string; description: string; icon: ElementType }> = {
  data: { title: 'Your data', icon: DnsOutlined, description: 'Turn approved documents and live application records into useful evidence for your Brain.' },
  integrations: { title: 'Integrations', icon: ExtensionOutlined, description: 'Reusable plugins connect your Brain to your systems. Your services remain the source of truth.' },
  retrieval: { title: 'Retrieval', icon: SearchOutlined, description: 'Connect a search engine and embedding provider, then ground answers in your approved knowledge.' },
  actions: { title: 'Actions', icon: BoltOutlined, description: 'Let AI propose useful next steps. Declared permissions, trusted targets and your confirmation policy govern execution.' },
  models: { title: 'AI models', icon: MemoryOutlined, description: 'Choose from the model providers supported by the current configuration editor. Select the model that fits your project.' },
  behaviour: { title: 'Behaviour', icon: TuneOutlined, description: 'Shape how your Brain responds, from a concise support answer to a more exploratory conversation.' },
  specialists: { title: 'Specialist teams', icon: HubOutlined, description: 'Give distinct work to distinct specialists, with supported sequential or parallel execution plans.' },
  human: { title: 'Human in the loop', icon: HeadsetMicOutlined, description: 'Keep a person within reach: continue an AI conversation with your team or review an important action.' },
  web: { title: 'Connect your web app', icon: WebOutlined, description: 'Add a branded AI workspace to your existing website, without rebuilding your application.' },
  mobile: { title: 'Connect your mobile app', icon: SmartphoneOutlined, description: 'Bring the same Brain to a mobile web experience or connect your own native interface.' },
  backend: { title: 'Call your Brain from a backend', icon: ApiOutlined, description: 'Send a scoped, authenticated request to your deployment-local conversation API.' },
  templates: { title: 'Choose your starting point', icon: LayersOutlined, description: 'Two available developer starters, built from the same configurable Platform, Engage and optional Desk products.' },
  connect: { title: 'Connect your tools', icon: SettingsInputComponentOutlined, description: 'Start with the information and services your app already uses. Add only the connections your Brain needs.' },
  test: { title: 'Test your Brain', icon: PlayCircleOutlineRounded, description: 'Use Try your AI in your Brain workspace to test questions, inspect evidence and check actions before connecting your app.' },
  use: { title: 'Use your Brain in your app', icon: CodeRounded, description: 'Choose an embedded workspace, a mobile-friendly address or a direct server integration.' },
}

function Options({ title, items, compact = false }: { title: string; items: VisualOption[]; compact?: boolean }) {
  return <section className="dev-explorer-section"><h3>{title}</h3><ul className={`dev-option-grid${compact ? ' dev-option-grid-compact' : ''}`}>
    {items.map(({ name, description, icon: Icon, logo, monochrome }) => <li key={name}><span className="dev-option-icon">
      {logo ? <img src={providerAsset + logo} alt="" width="30" height="30" className={monochrome ? 'dev-brand-monochrome' : undefined} /> : Icon && <Icon />}
    </span><span><strong>{name}</strong><small>{description}</small></span></li>)}</ul></section>
}

function Flow({ items }: { items: Array<{ label: string; icon: ElementType }> }) {
  return <ol className="dev-explorer-flow">{items.map(({ label, icon: Icon }, index) => <li key={label}><Icon /><strong>{label}</strong>{index < items.length - 1 && <ArrowForwardRounded className="dev-flow-arrow" />}</li>)}</ol>
}

const websiteExample = `<script
  async
  src="https://api.loomai.pro/api/public/ai-workspace/install.js"
  data-installation-id="YOUR_INSTALLATION_ID">
</script>`
const backendExample = `curl "$BRAIN_URL/api/chat/me/conversations/$CONVERSATION_ID/messages" \\
  -H "Authorization: Bearer $RUNTIME_TOKEN" \\
  -H "Idempotency-Key: $MESSAGE_ID" \\
  -H "Content-Type: application/json" \\
  --data '{"query":"Which option fits my team?"}'`

function CodeExample({ label, code }: { label: string; code: string }) {
  const [status, setStatus] = useState('')
  useEffect(() => { setStatus('') }, [code])
  const copy = async () => {
    try { await navigator.clipboard.writeText(code); setStatus('Copied') }
    catch { setStatus('Copy unavailable. Select the code to copy it.') }
  }
  return <div className="dev-code-example"><div><strong>{label}</strong><span role="status">{status}</span><Tooltip title="Copy example"><IconButton aria-label="Copy integration example" onClick={() => void copy()}>{status === 'Copied' ? <CheckRounded /> : <ContentCopyRounded />}</IconButton></Tooltip></div><pre><code>{code}</code></pre></div>
}

function WorkspaceIllustration({ mobile = false }: { mobile?: boolean }) {
  return <div className={`dev-channel-preview${mobile ? ' dev-channel-mobile' : ''}`} aria-label={mobile ? 'Illustrative mobile workspace' : 'Illustrative embedded website workspace'}>
    <div className="dev-channel-chrome">{mobile ? <SmartphoneOutlined /> : <WebOutlined />}<strong>{mobile ? 'Your branded workspace' : 'Your existing website'}</strong><small>Illustrative preview</small></div>
    <div className="dev-channel-scene">{!mobile && <div className="dev-host-content"><span /><span /><div><DescriptionOutlined /><strong>Your content</strong><small>Products, guides and services</small></div></div>}
      <div className="dev-channel-chat"><div><AutoAwesomeOutlined /><strong>Your app AI</strong></div><p className="dev-channel-question">Can you help me choose?</p><p>Your approved information helps me explain the options.</p><div className="dev-channel-evidence"><DescriptionOutlined /><span>Your product guide</span></div><div className="dev-channel-input"><span>Ask a follow-up...</span><ArrowForwardRounded /></div></div>
    </div>
  </div>
}

function IntegrationPreview({ initial }: { initial: typeof developerChannels[number]['id'] }) {
  const [channel, setChannel] = useState(initial)
  useEffect(() => { setChannel(initial) }, [initial])
  return <section className="dev-integration-preview"><ToggleButtonGroup exclusive value={channel} aria-label="Integration channel" onChange={(_, next) => { if (next) setChannel(next) }}>
    {developerChannels.map(({ id, name, icon: Icon }) => <ToggleButton key={id} value={id}><Icon />{name}</ToggleButton>)}
  </ToggleButtonGroup>
    {channel === 'web' ? <><WorkspaceIllustration /><Flow items={[{ label: 'Your website', icon: WebOutlined }, { label: 'Engage workspace', icon: ForumOutlined }, { label: 'Your assigned Brain', icon: MemoryOutlined }]} />
      <CodeExample label="Website installation" code={websiteExample} /><p className="dev-explorer-note">Use the installation ID from Engage, approve your exact website origin and activate it. Public anonymous installations need no app backend; private sign-in modes can require a reviewed broker or adapter.</p></>
      : channel === 'mobile' ? <><WorkspaceIllustration mobile /><Options title="Two ways to connect" items={[
        { name: 'Mobile web or in-app web view', description: 'Open your branded standalone HTTPS workspace.', icon: SmartphoneOutlined },
        { name: 'Your native interface', description: 'Use your own backend and the scoped runtime APIs.', icon: ApiOutlined },
      ]} /><CodeExample label="Example standalone address" code="https://your-project.apps.loomai.pro" /><p className="dev-explorer-note">The address is an example. Your verified Engage address becomes available after configuration, readiness checks and activation. No native mobile SDK is implied.</p></>
        : <><Flow items={[{ label: 'Your server', icon: DnsOutlined }, { label: 'Scoped runtime API', icon: ApiOutlined }, { label: 'Evidence & actions', icon: AutoAwesomeOutlined }]} />
          <CodeExample label="Conversation message API" code={backendExample} /><dl className="dev-api-fields"><div><dt>BRAIN_URL</dt><dd>Your assigned deployment address.</dd></div><div><dt>RUNTIME_TOKEN</dt><dd>A runtime access token from the configured issuer, authorized for conversation messages. Not an OpenAI key.</dd></div><div><dt>CONVERSATION_ID</dt><dd>The same owner-scoped ID for each turn in the conversation.</dd></div><div><dt>MESSAGE_ID</dt><dd>A unique message key, reused when retrying that message.</dd></div></dl>
          <p className="dev-explorer-note">This example uses the canonical conversation-message route when conversation control is enabled. Use your published connection's exact routes and authentication. Provider credentials and private assertions stay server-side.</p></>}
  </section>
}

function TopicContent({ topic }: { topic: DeveloperTopic }) {
  if (topic === 'web' || topic === 'mobile' || topic === 'backend' || topic === 'use') return <IntegrationPreview initial={topic === 'use' ? 'web' : topic} />
  if (topic === 'models') return <><Options title="Supported model providers" items={modelProviders} /><p className="dev-explorer-note">Model IDs and availability depend on your provider account. Credentials, model usage and resource allowances still apply.</p></>
  if (topic === 'retrieval') return <><Options title="Search engines & vector stores" items={searchEngines} /><Options title="Embedding providers" compact items={embeddingProviders.map(item => ({ ...item, description: item.name === 'ONNX' ? 'Local embeddings' : 'Provider embeddings' }))} /><p className="dev-explorer-note">These are supported configuration options, not already-connected services. Select compatible models, dimensions and an authorized knowledge scope.</p></>
  if (topic === 'templates') return <div className="dev-template-choices">{[
    { code: 'ai-customer-experience' as const, name: 'Customer Experience Brain', icon: ForumOutlined, description: 'Answers, useful actions and a branded website or standalone workspace.', products: 'Platform + Engage' },
    { code: 'ai-support-experience' as const, name: 'Support Team Brain', icon: HeadsetMicOutlined, description: 'Approved support knowledge, optional requests and a human handover path.', products: 'Platform + Engage + optional Desk' },
  ].map(({ code, name, icon: Icon, description, products }) => <article key={code}><Icon /><h3>{name}</h3><p>{description}</p><small>{products} · Staging only</small><a className="dev-button" href={developerConsoleHref(developerKitSetupPath(code))}>Use this starter<ArrowForwardRounded /></a></article>)}</div>
  if (topic === 'data') return <><Flow items={[{ label: 'Approved sources', icon: DescriptionOutlined }, { label: 'Index & sync', icon: SyncOutlined }, { label: 'Grounded answers', icon: AutoAwesomeOutlined }]} /><Options title="Bring your information" items={connections.slice(0, 2).concat([{ name: 'Source ownership', description: 'Keep documents and records in their authorized scope.', icon: ShieldOutlined }])} /></>
  if (topic === 'integrations' || topic === 'connect') return <><Options title="Connect through existing plugins" items={connections} /><Flow items={[{ label: 'Your systems', icon: DnsOutlined }, { label: 'Configured plugin', icon: ExtensionOutlined }, { label: 'Your Brain', icon: MemoryOutlined }]} /><p className="dev-explorer-note">Add endpoints and secret references in the existing connection editors. No customer API or storage account is connected automatically by this preview.</p></>
  if (topic === 'actions') return <><Options title="What an action can do" items={[
    { name: 'Read information', description: 'Fetch current facts from an authorized API.', icon: SearchOutlined },
    { name: 'Write an update', description: 'Call a declared application endpoint.', icon: EditNoteOutlined },
    { name: 'Ask for confirmation', description: 'Let the user accept configured changes.', icon: ShieldOutlined },
    { name: 'Request human review', description: 'Route supported decisions to an authorized reviewer.', icon: FactCheckOutlined },
  ]} /><Flow items={[{ label: 'AI proposes', icon: AutoAwesomeOutlined }, { label: 'Your policy checks', icon: ShieldOutlined }, { label: 'Your service executes', icon: ApiOutlined }]} /></>
  if (topic === 'behaviour') return <Options title="Four ways to shape the conversation" items={[
    { name: 'Tone', description: 'Friendly, professional or your own voice.', icon: RecordVoiceOverOutlined },
    { name: 'Answer breadth', description: 'Grounded answers or permitted general advice.', icon: OpenInFullRounded },
    { name: 'Response length', description: 'Short replies or more detailed explanations.', icon: FormatAlignLeftRounded },
    { name: 'Helpful next steps', description: 'Contextual suggestions when useful.', icon: AltRouteRounded },
  ]} />
  if (topic === 'specialists') return <><Options title="Compose the right working pattern" items={[
    { name: 'Sequential specialists', description: 'One worker passes its output to the next.', icon: AltRouteRounded },
    { name: 'Parallel specialists', description: 'Distinct workers contribute independently.', icon: HubOutlined },
  ]} /><Flow items={[{ label: 'Conversation manager', icon: ForumOutlined }, { label: 'Specialist work', icon: HubOutlined }, { label: 'Combined result', icon: AutoAwesomeOutlined }]} /><p className="dev-explorer-note">Configuration and runtime readiness must be qualified before use; specialist teams are not automatically enabled by a starter Kit.</p></>
  if (topic === 'human') return <><div className="dev-human-preview"><img src={`${import.meta.env.BASE_URL}products/demo-support-agent.webp`} alt="Illustrative human support agent" width="56" height="56" /><div><strong>Your team, when it matters</strong><p>Continue the conversation in LoomAI Desk.</p></div><HeadsetMicOutlined /></div><Options title="Two human-support paths" items={[
    { name: 'Conversation handover', description: 'Move between AI and an authorized human agent.', icon: ForumOutlined },
    { name: 'Action review', description: 'Let an authorized person review supported proposals.', icon: FactCheckOutlined },
  ]} /><p className="dev-explorer-note">Connect Desk, assign your agents and enable the required capabilities before use.</p></>
  return <><div className="dev-test-preview" aria-label="Illustrative Brain test"><div><PlayCircleOutlineRounded /><strong>Try your AI</strong><small>Illustrative preview</small></div><p className="dev-test-question">What does our delivery policy say?</p><p>An answer based on your approved delivery guide, with its source attached.</p><span><DescriptionOutlined />Delivery guide · Approved evidence</span></div><Options title="What to check" items={[
    { name: 'Answers & evidence', description: 'Review the answer and its authorized sources.', icon: SearchOutlined },
    { name: 'Actions & confirmation', description: 'Check parameters and configured approval steps.', icon: BoltOutlined },
    { name: 'Follow-up questions', description: 'Test a full conversation, not only one query.', icon: ForumOutlined },
    { name: 'Human handover', description: 'Verify the transition when Desk is connected.', icon: HeadsetMicOutlined },
  ]} /><p className="dev-explorer-note">Testing uses your configured runtime and can consume provider usage. This preview sends no AI requests.</p></>
}

export function DeveloperExplorerDialog({ topic, onClose }: { topic: DeveloperTopic | null; onClose: () => void }) {
  const item = topic ? content[topic] : null
  const Icon = item?.icon
  return <Dialog open={Boolean(item)} onClose={onClose} maxWidth="md" fullWidth className="dev-capability-dialog" data-explorer-topic={topic ?? undefined}>
    <DialogTitle><span className="dev-dialog-heading">{Icon && <span className="dev-dialog-icon"><Icon /></span>}<span><small>EXPLORE YOUR SMART BRAIN</small>{item?.title}</span></span><IconButton aria-label="Close capability preview" onClick={onClose} className="dev-dialog-close"><CloseRounded /></IconButton></DialogTitle>
    <DialogContent><p className="dev-dialog-intro">{item?.description}</p>{topic && <TopicContent topic={topic} />}</DialogContent>
    <DialogActions><span>Developer starters · Staging only</span><a className="dev-button" href="#developer-brains" onClick={onClose}>Choose your starting point<ArrowForwardRounded /></a></DialogActions>
  </Dialog>
}
