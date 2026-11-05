import { Button } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import StorefrontOutlined from '@mui/icons-material/StorefrontOutlined'
import DirectionsCarOutlined from '@mui/icons-material/DirectionsCarOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import SearchRounded from '@mui/icons-material/SearchRounded'
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded'
import ShoppingBagOutlined from '@mui/icons-material/ShoppingBagOutlined'
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import ConfirmationNumberOutlined from '@mui/icons-material/ConfirmationNumberOutlined'
import WebAssetOutlined from '@mui/icons-material/WebAssetOutlined'
import DesktopWindowsOutlined from '@mui/icons-material/DesktopWindowsOutlined'
import OpenInBrowserOutlined from '@mui/icons-material/OpenInBrowserOutlined'
import { Link } from 'react-router-dom'
import type { ExperienceKitDefinition } from '../../api/experienceKitsApi'
import { KitDeskMark, KitMark } from './KitIdentity'
import { KitExperiencePreview } from './KitCapabilityPreview'
import { KitCardScene } from './KitCardScene'
import { KitDealershipOffer } from './KitCommercialPresentation'
import { kitPreviewKind, safeKitExternalUrl } from './kitModels'
import './KitCatalogueCards.css'

const cardContent = {
  adaptive: { scope: 'Adapts to your business', title: 'AI Customer Experience', summary: 'Help customers discover, decide and act.', Icon: StorefrontOutlined,
    features: [[SearchRounded, 'Discover'], [CompareArrowsRounded, 'Compare'], [ShoppingBagOutlined, 'Take action']] },
  dealership: { scope: 'Built for dealerships', title: 'Dealership AI', summary: 'Turn live stock into confident enquiries.', Icon: DirectionsCarOutlined,
    features: [[DirectionsCarOutlined, 'Live stock'], [CompareArrowsRounded, 'Compare'], [CalendarMonthOutlined, 'Enquiries']] },
  support: { scope: 'Built for support teams', title: 'AI Support', summary: 'Resolve questions. Keep people connected.', Icon: HeadsetMicOutlined,
    features: [[DescriptionOutlined, 'Answers'], [ConfirmationNumberOutlined, 'Requests'], [HeadsetMicOutlined, 'Human handover']] },
} as const

function CardProducts() {
  return <div className="ek-card-products" aria-label="Products included: Platform and Engage, with optional Human Desk"><div>
    <span><KitMark product="platform" />Platform</span><b aria-hidden="true">+</b>
    <span><KitMark product="engage" />Engage</span><b aria-hidden="true">+</b>
    <span><KitDeskMark />Human Desk</span>
  </div><small>Human support optional with Desk</small></div>
}

export function KitChooser({ definitions, authenticated = false, detailLinks = true }: {
  definitions: ExperienceKitDefinition[]; authenticated?: boolean; detailLinks?: boolean
}) {
  return <><div className="ek-catalogue-grid">{[...definitions].sort((a, b) =>
    ['adaptive', 'dealership', 'support'].indexOf(kitPreviewKind(a.code)) - ['adaptive', 'dealership', 'support'].indexOf(kitPreviewKind(b.code))).map(definition => {
    const dealership = definition.code === 'dealership-ai-experience'
    const kind = kitPreviewKind(definition.code)
    const demo = safeKitExternalUrl(definition.demoUrl)
    const copy = cardContent[kind]
    return <div className="ek-kit-option" data-kind={kind} key={definition.code}>
      {dealership ? <KitDealershipOffer compact /> : <div className="ek-offer-placeholder" aria-hidden="true" />}
      <article className="ek-catalogue-card" aria-labelledby={`ek-choice-${definition.code}`}>
        <div className="ek-card-identity"><span className="ek-card-scope"><copy.Icon />{copy.scope}</span>
          <h2 id={`ek-choice-${definition.code}`}>{copy.title}</h2><p>{copy.summary}</p></div>
        <KitCardScene kind={kind} />
        <div className="ek-card-capabilities">{copy.features.map(([Icon, label]) => <span key={label}><Icon />{label}</span>)}</div>
        <CardProducts />
        <Button component={Link} variant="contained" className="ek-card-explore" endIcon={<ArrowForwardRounded />}
          to={detailLinks ? `/experiences/${definition.code}` : authenticated ? `/kits/new/${definition.code}` : `/sign-up?kit=${definition.code}`}>{detailLinks ? 'Explore kit' : 'Install kit'}</Button>
        <div className="ek-card-preview-command"><KitExperiencePreview kind={kind} name={definition.name} compact />
          {demo && <a className="ek-card-demo" href={demo} target="_blank" rel="noopener noreferrer"><PlayCircleOutlineRounded />{dealership ? 'Try live demo' : 'Try demo'}</a>}</div>
      </article>
    </div>
  })}</div><div className="ek-catalogue-formats" aria-label="Ways to use your AI experience">
    <small>Choose one or combine them.</small><span><WebAssetOutlined />Website widget</span><span><DesktopWindowsOutlined />Max workspace</span><span><OpenInBrowserOutlined />Standalone app</span>
  </div><p className="ek-catalogue-disclosure">Illustrative previews. Configure your own branding, knowledge and integrations.</p></>
}
