import AttachFileRounded from '@mui/icons-material/AttachFileRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import MoreHorizRounded from '@mui/icons-material/MoreHorizRounded'
import PersonRounded from '@mui/icons-material/PersonRounded'
import SendRounded from '@mui/icons-material/SendRounded'
import SmartToyOutlined from '@mui/icons-material/SmartToyOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import LanguageRounded from '@mui/icons-material/LanguageRounded'
import { resolveWorkspacePackPresentation } from '../customer/buildWorkspacePreviewConfiguration'
import { KitMark, kitAsset } from './KitIdentity'
import type { CSSProperties } from 'react'
import { SupportKitIllustration } from './SupportKitPreview'

type Props = {
  packCode: string
  workspaceName: string
  brandColor: string
  humanSupportEnabled?: boolean
  ticketingEnabled?: boolean
  configuration?: Record<string, unknown>
  layout?: 'embedded' | 'standalone'
  compact?: boolean
  dark?: boolean
  appUrl?: string | null
}

/** Presentation only: no runtime connection, visitor state, action handlers or AI requests. */
export function KitWorkspacePreview({ packCode, workspaceName, brandColor, humanSupportEnabled = false, ticketingEnabled = false,
  configuration = {}, layout = 'standalone', compact = false, dark = false, appUrl }: Props) {
  if (packCode === 'support') return <SupportKitIllustration workspaceName={workspaceName} brandColor={brandColor} ticketingEnabled={ticketingEnabled}
    humanSupportEnabled={humanSupportEnabled} configuration={configuration} compact={compact} appUrl={appUrl} layout={layout} />
  const dealership = packCode === 'dealership'
  const presentation = resolveWorkspacePackPresentation(configuration, packCode)
  const groups = presentation?.toolGroups as { default?: { label?: string; tools?: Array<{ label?: string }> } } | undefined
  const tools = groups?.default?.tools?.filter(tool => typeof tool.label === 'string').slice(0, 4) ?? []
  return <figure className={`ek-workspace-figure${compact ? ' ek-preview-compact' : ''}`}>
    <div className={`ek-workspace-preview${dark ? ' ek-preview-dark' : ''}${layout === 'embedded' ? ' ek-preview-embedded' : ''}`}
      style={{ '--ek-preview-brand': brandColor } as CSSProperties} role="img"
      aria-label={`Illustrative ${layout === 'embedded' ? 'website widget' : 'standalone app'} preview for ${workspaceName || 'your workspace'}. Example content, not live answers or an assigned agent.`}>
      <div aria-hidden="true" className="ek-workspace-interior">
        {layout === 'embedded' && <div className="ek-example-site"><span>Your website</span><div /><div /></div>}
        <div className="ek-preview-window">
          {layout === 'standalone' && appUrl && <div className="ek-preview-address"><LanguageRounded /><span>{appUrl}</span></div>}
          <div className="ek-preview-header"><KitMark product="engage" /><div><strong>{workspaceName.trim() || 'Your company AI'}</strong><small>{dealership ? 'Your dealership workspace' : 'Your company assistant'}</small></div><MoreHorizRounded /></div>
          {!compact && tools.length > 0 && <div className="ek-preview-tools">{tools.map((tool, index) => <span key={`${tool.label}:${index}`}>{tool.label}</span>)}</div>}
          <div className="ek-preview-messages">
            <div className="ek-preview-message ek-preview-user"><span className="ek-preview-avatar"><PersonRounded /></span><p>{dealership ? 'Help me explore these cars and compare my options.' : 'Help me find information in our approved guides.'}</p></div>
            <div className="ek-preview-message ek-preview-ai"><span className="ek-preview-avatar"><SmartToyOutlined /></span><div>
              <p>{dealership ? 'Explore the example inventory and ask about the details that matter to you.' : 'Your connected knowledge and services can help answer questions and guide the next step.'}</p>
              {dealership ? <div className="ek-preview-vehicle"><img src={kitAsset('demo-vehicle-01.webp')} alt="" width="320" height="200" loading="lazy" />
                <div><strong>2025 Aster E1</strong><span>GBP 31,950</span><small>Fictional demonstration inventory</small></div></div>
                : <div className="ek-preview-knowledge"><DescriptionOutlined /><div><strong>Example guide</strong><small>Your approved business knowledge</small></div></div>}
              <div className="ek-preview-source"><DescriptionOutlined />{dealership ? 'Demo stock' : 'Example source'}<ArrowForwardRounded /></div>
            </div></div>
            {humanSupportEnabled && <>
              <div className="ek-preview-handover"><HeadsetMicOutlined /><span>Human support preview</span></div>
              <div className="ek-preview-message ek-preview-agent"><img className="ek-preview-avatar" src={kitAsset('demo-support-agent.webp')} alt="" width="36" height="36" loading="lazy" /><div><small>Example agent</small><p>Your own team can continue the conversation here.</p></div></div>
            </>}
          </div>
          <div className="ek-preview-input"><AttachFileRounded /><span>Type a message...</span><SendRounded /></div>
        </div>
      </div>
    </div>
    <figcaption>Illustrative preview. Sample content only.</figcaption>
  </figure>
}
