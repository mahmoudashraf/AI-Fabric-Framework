import { Button } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import { Link } from 'react-router-dom'
import type { ExperienceKitDefinition } from '../../api/experienceKitsApi'
import { KitMark } from './KitIdentity'
import { KitCapabilityIllustration, KitExperiencePreview, KitScope } from './KitCapabilityPreview'
import { kitPreviewKind, safeKitExternalUrl } from './kitModels'

export function KitChooser({ definitions, authenticated = false, detailLinks = true }: {
  definitions: ExperienceKitDefinition[]; authenticated?: boolean; detailLinks?: boolean
}) {
  return <div className="ek-chooser-grid ek-capability-grid">{definitions.map(definition => {
    const dealership = definition.code === 'dealership-ai-experience'
    const kind = kitPreviewKind(definition.code)
    const demo = safeKitExternalUrl(definition.demoUrl)
    return <article key={definition.code} data-kind={kind} className="ek-capability-choice" aria-labelledby={`ek-choice-${definition.code}`}>
      <div className="ek-capability-identity"><KitMark product={dealership ? 'dealership' : 'engage'} large /><div>
        <KitScope kind={kind} /><h2 id={`ek-choice-${definition.code}`}>{definition.name}</h2>
      </div></div>
      <KitCapabilityIllustration kind={kind} />
      <KitExperiencePreview kind={kind} name={definition.name}>
        <Button component={Link} variant="contained" endIcon={<ArrowForwardRounded />}
          to={detailLinks ? `/experiences/${definition.code}` : authenticated ? `/kits/new/${definition.code}` : `/sign-up?kit=${definition.code}`}>Get started</Button>
      </KitExperiencePreview>
      {demo ? <a className="ek-cap-demo-link" href={demo} target="_blank" rel="noopener noreferrer"><PlayCircleOutlineRounded />{dealership ? 'Try the dealership demo' : 'Try demo'} <ArrowForwardRounded /></a>
        : <p className="ek-cap-demo-link ek-muted">Illustrative preview. Make it yours.</p>}
    </article>
  })}</div>
}
