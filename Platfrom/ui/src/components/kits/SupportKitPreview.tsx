import { useId, useState, type CSSProperties, type ReactNode } from 'react'
import { Button, Dialog, IconButton, ToggleButton, ToggleButtonGroup, Tooltip, useMediaQuery, useTheme } from '@mui/material'
import AttachFileRounded from '@mui/icons-material/AttachFileRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import ConfirmationNumberOutlined from '@mui/icons-material/ConfirmationNumberOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import OpenInFullRounded from '@mui/icons-material/OpenInFullRounded'
import VisibilityOutlined from '@mui/icons-material/VisibilityOutlined'
import PersonRounded from '@mui/icons-material/PersonRounded'
import SendRounded from '@mui/icons-material/SendRounded'
import { supportExperiencePreviewFixtures } from '../../../../../experience-packs/support-experience/src/preview-fixtures'
import { KitMark, kitAsset } from './KitIdentity'
import { kitPreviewFormats, type KitPreviewFormat } from './kitPreviewContent'
import { KitDeliveryScene } from './KitDetailScenes'
import WebAssetOutlined from '@mui/icons-material/WebAssetOutlined'
import DesktopWindowsOutlined from '@mui/icons-material/DesktopWindowsOutlined'
import OpenInBrowserOutlined from '@mui/icons-material/OpenInBrowserOutlined'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'

export function supportKitPreviewFixtures({ workspaceName = 'Your support AI', brandColor = '#08695c', ticketingEnabled = true,
  humanSupportEnabled = false, configuration = {} }: { workspaceName?: string; brandColor?: string; ticketingEnabled?: boolean;
  humanSupportEnabled?: boolean; configuration?: Record<string, unknown> } = {}) {
  const copy = configuration.copy as { welcomeMessage?: string; placeholder?: string } | undefined
  return supportExperiencePreviewFixtures({ schemaVersion: 'loomai-support-experience-config-v1',
    app: { assistantLabel: workspaceName || 'Your support AI', title: 'Help and support' },
    support: { requestLabel: 'My Requests', teamVisible: humanSupportEnabled },
    capabilities: { tickets: ticketingEnabled, handover: humanSupportEnabled, attachments: true, currentPage: false },
    theme: { primaryColor: brandColor }, copy: {
      ...(typeof copy?.welcomeMessage === 'string' ? { welcomeMessage: copy.welcomeMessage } : {}),
      ...(typeof copy?.placeholder === 'string' ? { placeholder: copy.placeholder } : {}),
    } })
}

type Props = Parameters<typeof supportKitPreviewFixtures>[0] & { compact?: boolean; appUrl?: string | null; layout?: 'embedded' | 'standalone' }

/** The pack's synthetic fixtures only: no requester, delivery, transcript or ticket command implementation. */
export function SupportKitIllustration({ compact = false, appUrl, layout = 'standalone', ...options }: Props) {
  const fixture = supportKitPreviewFixtures(options)
  const { presentation, requests } = fixture.support
  return <figure className={`ek-cap-illustration ek-support-illustration ek-support-layout-${layout}${compact ? ' ek-support-compact' : ''}`} role="img"
    style={{ '--ek-cap-accent': presentation.theme.primaryColor } as CSSProperties}
    aria-label={`Illustrative support ${layout === 'embedded' ? 'website widget' : 'workspace'} for ${presentation.assistantLabel}. Synthetic guidance and requests; nothing submitted.`}>
    <div aria-hidden="true">
      {layout === 'standalone' && appUrl && <div className="ek-support-preview-address">{appUrl}</div>}
      <div className="ek-cap-heading"><KitMark product="engage" /><strong>{presentation.assistantLabel}</strong><span>Sample only</span></div>
      <div className="ek-cap-conversation">
        <div className="ek-cap-query"><PersonRounded /><p>{fixture.userMessage}</p></div>
        <div className="ek-cap-answer"><KitMark product="engage" /><p>{fixture.answer}</p></div>
        {!compact && <div className="ek-support-guidance"><DescriptionOutlined /><div><strong>Approved support guidance</strong><small>Answers grounded in your connected knowledge</small></div></div>}
        {requests.map(request => <div className="ek-support-sample-request" key={request.id}><ConfirmationNumberOutlined /><div>
          <strong>{request.reference} &middot; {request.subject}</strong><small>{request.status} &middot; Linked conversation</small><p>{request.details}</p></div></div>)}
        {presentation.handover && <div className="ek-cap-agent"><img src={kitAsset('demo-support-agent.webp')} alt="" width="40" height="40" />
          <span><strong>Example support team</strong><small>Optional human support with LoomAI Desk</small></span><HeadsetMicOutlined /></div>}
        <div className="ek-cap-composer"><AttachFileRounded /><span>{presentation.placeholder}</span><SendRounded /></div>
      </div>
    </div>
    <figcaption className="ek-support-preview-note">Synthetic preview. No live AI or request submitted.</figcaption>
  </figure>
}

export function SupportKitExperiencePreview({ name, children, compact = false, detail = false }: { name: string; children?: ReactNode; compact?: boolean; detail?: boolean }) {
  const [format, setFormat] = useState<KitPreviewFormat>('max')
  const [open, setOpen] = useState(false)
  const [tickets, setTickets] = useState(true)
  const [human, setHuman] = useState(false)
  const narrow = useMediaQuery('(max-width:650px)')
  const { palette } = useTheme()
  const titleId = useId()
  const show = (next: KitPreviewFormat) => { setFormat(next); setOpen(true) }
  return <>
    {!compact && <section className={`ek-experience-formats${detail ? ' ek-detail-formats' : ''}`} aria-label={`Ways to use ${name}`}>
      {!detail && <h3>Support on your website or in its own workspace</h3>}
      <div className="ek-format-grid">{kitPreviewFormats.map(item => <button type="button" key={item.id} onClick={() => show(item.id)} aria-label={`Preview ${item.label} for ${name}`}>
        {detail ? <><span className="ek-detail-format-label">{item.id === 'website' ? <WebAssetOutlined /> : item.id === 'max' ? <DesktopWindowsOutlined /> : <OpenInBrowserOutlined />}{item.label}</span>
          <KitDeliveryScene kind="support" format={item.id} /><strong>{item.id === 'website' ? 'Help where questions happen' : item.id === 'max' ? 'Work through the details together' : 'A dedicated place for support'}</strong><small>{item.description}<ArrowForwardRounded /></small></>
          : <><span className={`ek-format-screen ek-support-format ek-format-${item.id}`} aria-hidden="true"><KitMark product="engage" /><DescriptionOutlined /><ConfirmationNumberOutlined /></span><strong>{item.label}</strong></>}
      </button>)}</div>
    </section>}
    <div className="ek-cap-commands">{children}<Button variant={compact ? 'text' : 'outlined'} startIcon={compact ? <VisibilityOutlined /> : <OpenInFullRounded />} onClick={() => show('max')}>{compact ? 'Preview support' : 'Preview experience'}</Button></div>
    <Dialog open={open} onClose={() => setOpen(false)} fullWidth maxWidth="md" fullScreen={narrow} aria-labelledby={titleId}
      PaperProps={{ className: 'ek-page ek-experience-dialog', 'data-kit-theme': palette.mode } as React.HTMLAttributes<HTMLDivElement>}>
      <div className="ek-experience-dialog-header"><h2 id={titleId}>Support experience preview</h2><Tooltip title="Close preview"><IconButton aria-label="Close preview" onClick={() => setOpen(false)}><CloseRounded /></IconButton></Tooltip></div>
      <div className="ek-experience-format-picker"><ToggleButtonGroup exclusive value={format} aria-label="Workspace preview format" onChange={(_, value: KitPreviewFormat | null) => { if (value) setFormat(value) }}>
        {kitPreviewFormats.map(item => <ToggleButton key={item.id} value={item.id}>{item.label}</ToggleButton>)}
      </ToggleButtonGroup></div>
      <div className="ek-support-preview-options"><label><input type="checkbox" checked={tickets} onChange={event => setTickets(event.target.checked)} />Request tracking sample</label>
        <label><input type="checkbox" checked={human} onChange={event => setHuman(event.target.checked)} />Human support sample</label></div>
      <div className="ek-experience-dialog-body"><SupportKitIllustration workspaceName={name} ticketingEnabled={tickets} humanSupportEnabled={human}
        appUrl={format === 'standalone' ? 'https://support.your-business.example' : null} layout={format === 'website' ? 'embedded' : 'standalone'} /></div>
    </Dialog>
  </>
}
