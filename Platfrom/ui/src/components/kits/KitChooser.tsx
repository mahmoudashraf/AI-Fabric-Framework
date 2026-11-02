import { Button } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import MenuBookOutlined from '@mui/icons-material/MenuBookOutlined'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import WebOutlined from '@mui/icons-material/WebOutlined'
import StorageOutlined from '@mui/icons-material/StorageOutlined'
import CompareArrowsRounded from '@mui/icons-material/CompareArrowsRounded'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import HeadsetMicOutlined from '@mui/icons-material/HeadsetMicOutlined'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import { Link } from 'react-router-dom'
import type { ExperienceKitDefinition } from '../../api/experienceKitsApi'
import { KitMark, KitProductLine, kitAsset } from './KitIdentity'
import { KitWorkspacePreview } from './KitWorkspacePreview'
import { safeKitExternalUrl } from './kitModels'

export function KitChooser({ definitions, authenticated = false, detailLinks = true }: {
  definitions: ExperienceKitDefinition[]; authenticated?: boolean; detailLinks?: boolean
}) {
  return <div className="ek-chooser-grid">{definitions.map(definition => {
    const dealership = definition.code === 'dealership-ai-experience'
    const icons = dealership ? [StorageOutlined, CompareArrowsRounded, DescriptionOutlined] : [MenuBookOutlined, SettingsOutlined, WebOutlined]
    const demo = safeKitExternalUrl(definition.demoUrl)
    return <article key={definition.code} className={`ek-choice${dealership ? ' ek-choice-dealership' : ''}`} aria-labelledby={`ek-choice-${definition.code}`}>
      <div className="ek-choice-heading"><KitMark product={dealership ? 'dealership' : 'engage'} large />
        <div><h2 id={`ek-choice-${definition.code}`}>{definition.name}</h2><p>{definition.summary}</p></div></div>
      <div className="ek-choice-body">
        <KitWorkspacePreview compact dark packCode={definition.experiencePackCode} workspaceName={definition.defaultWorkspaceName}
          brandColor={dealership ? '#409aff' : '#28bfa6'} />
        <div className="ek-choice-features">
          {dealership && <div className="ek-choice-photo"><img src={kitAsset('demo-vehicle-01.webp')} alt="Blue car from the fictional dealership demo" width="480" height="280" loading="lazy" />
            <span className="ek-badge ek-badge-amber">Simulator demo</span><strong>2025 Aster E1</strong></div>}
          <div className="ek-feature-list">{definition.features.map((feature, index) => {
            const Icon = icons[index % icons.length]
            return <div key={`${feature}:${index}`}><Icon /><span>{feature}</span></div>
          })}</div>
          <div className="ek-choice-products"><small>Built with LoomAI products</small><KitProductLine dealership={dealership} /></div>
          <div className="ek-desk-optional"><HeadsetMicOutlined /><span>Human support available with Desk</span></div>
        </div>
      </div>
      <div className="ek-choice-actions"><Button component={Link} variant="contained" endIcon={<ArrowForwardRounded />}
        to={detailLinks ? `/experiences/${definition.code}` : authenticated ? `/kits/new/${definition.code}` : `/sign-up?kit=${definition.code}`}>
        {detailLinks ? dealership ? 'Explore dealership kit' : 'Explore experience' : 'Set up this kit'}</Button>
        {demo && <Button component="a" href={demo} target="_blank" rel="noopener noreferrer" startIcon={<PlayCircleOutlineRounded />}>Try demo</Button>}
      </div>
    </article>
  })}</div>
}
