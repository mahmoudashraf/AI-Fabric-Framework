import { Button } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CalendarMonthOutlined from '@mui/icons-material/CalendarMonthOutlined'
import CheckRounded from '@mui/icons-material/CheckRounded'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import SupportAgentRounded from '@mui/icons-material/SupportAgentRounded'
import RocketLaunchOutlined from '@mui/icons-material/RocketLaunchOutlined'
import { Link } from 'react-router-dom'
import { IntegrationCallButton } from '../IntegrationCallButton'
import type { ExperienceKitDefinition } from '../../api/experienceKitsApi'
import { KitExperiencePreview } from './KitCapabilityPreview'
import { dealershipOffer } from './KitCommercialPresentation'
import { KitDetailWorkspace, KitOutcomeScene } from './KitDetailScenes'
import { KitDeskMark, KitMark, kitAsset } from './KitIdentity'
import { KitIntegrationJourney } from './KitIntegrationJourney'
import { kitDetailContent } from './kitDetailContent'
import { kitPreviewKind, safeKitExternalUrl } from './kitModels'
import './KitDetailPage.css'

export function KitDetailPage({ definition }: { definition: ExperienceKitDefinition; authenticated: boolean }) {
  const kind = kitPreviewKind(definition.code)
  const copy = kitDetailContent[kind]
  const dealership = kind === 'dealership'
  const demo = safeKitExternalUrl(definition.demoUrl)
  const getStartedPath = `/kits/new/${definition.code}`
  return <article className="ek-public-detail ek-detail-story" data-kind={kind} aria-labelledby="ek-detail-title">
    <section className="ek-detail-hero">
      <img className="ek-detail-hero-photo" src={kitAsset(copy.image)} alt="" width="1920" height="768" fetchPriority="high" />
      <div className="ek-detail-hero-inner">
        <div className="ek-detail-hero-copy"><Link className="ek-detail-back" to="/experiences"><ArrowBackRounded />Back to experiences</Link>
          <span className="ek-detail-eyebrow">{copy.scope}</span><h1 id="ek-detail-title" aria-label={definition.name}>{copy.title}</h1>
          <p className="ek-detail-headline">{copy.headline}</p><p className="ek-detail-summary">{copy.summary}</p>
          <div className="ek-detail-hero-actions">
            <Button component={Link} to={getStartedPath} variant="contained" startIcon={<RocketLaunchOutlined />}>Install kit</Button>
            <IntegrationCallButton variant="outlined">Start a project</IntegrationCallButton>
          </div>
          <p className="ek-detail-start-note">Configure your kit in Console, or plan the integration with our team.</p>
          {demo ? <a className="ek-detail-preview-link" href={demo} target="_blank" rel="noopener noreferrer"><PlayCircleOutlineRounded />Try the live demo</a>
            : <a className="ek-detail-preview-link" href="#ek-detail-experience">Preview the experience<ArrowForwardRounded /></a>}
        </div>
        <figure className="ek-detail-laptop" role="img" aria-label={`Illustrative ${copy.title} workspace. Fictional example, not live AI.`}>
          <div className="ek-detail-laptop-display"><KitDetailWorkspace kind={kind} /></div><div className="ek-detail-laptop-base" aria-hidden="true" />
          <figcaption>Illustrative workspace · Your brand, knowledge and integrations</figcaption>
        </figure>
      </div>
    </section>

    {dealership ? <aside className="ek-detail-launch" aria-label="Dealership offer"><span className="ek-detail-offer-label">Dealership launch offer</span>
      <div><CalendarMonthOutlined /><span><strong>3 months free</strong><small>{dealershipOffer.benefits[0]}</small></span></div>
      <div><SupportAgentRounded /><span><strong>{dealershipOffer.benefits[1]}</strong><small>LoomAI's technical team handles the setup.</small></span></div>
      <div className="ek-detail-offer-price"><span>{dealershipOffer.price}</span><strong><CheckRounded />{dealershipOffer.value}</strong></div>
    </aside> : <div className="ek-detail-intro-band"><span><KitMark product="platform" />Connected to your business</span><span><KitMark product="engage" />Built around your brand</span>
      <span><SupportAgentRounded />Integration handled by LoomAI</span></div>}

    <section id="ek-detail-experience" className="ek-detail-band ek-detail-delivery" aria-labelledby="ek-detail-delivery-title">
      <h2 id="ek-detail-delivery-title">{copy.transformation}</h2><p className="ek-detail-lead">{copy.introduction}</p>
      <KitExperiencePreview kind={kind} name={definition.name} detail />
    </section>

    <section className="ek-detail-band" aria-labelledby="ek-detail-outcomes-title"><h2 id="ek-detail-outcomes-title">{copy.outcomesTitle}</h2>
      <div className="ek-detail-outcomes">{copy.outcomes.map((outcome, index) => <div className="ek-detail-outcome" key={outcome.title}>
        <h3><span>{index + 1}</span>{outcome.title}</h3><KitOutcomeScene kind={kind} step={index} /><p>{outcome.description}</p>
        {index < 2 && <ArrowForwardRounded className="ek-detail-outcome-arrow" aria-hidden="true" />}
      </div>)}<aside className="ek-detail-human"><img src={kitAsset('demo-support-agent-headset.webp')} alt="Fictional example support agent wearing a headset" width="160" height="160" loading="lazy" />
        <strong>Your people, when needed</strong><p>{kind === 'support' ? 'Choose human support and request tracking separately.' : 'An optional handover to your own assigned team.'}</p><small>Optional with LoomAI Desk</small></aside></div>
    </section>

    <KitIntegrationJourney kind={kind} getStartedPath={getStartedPath} compact />

    <section className="ek-detail-products" aria-label="Products included in this experience">
      <Link to="/products/platform"><KitMark product="platform" /><span><strong>LoomAI Platform</strong><small>{copy.knowledge}</small></span><ArrowForwardRounded /></Link>
      <Link to="/products/engage"><KitMark product="engage" /><span><strong>LoomAI Engage</strong><small>Your branded AI workspace</small></span><ArrowForwardRounded /></Link>
      <Link to="/products/desk"><KitDeskMark /><span><strong>LoomAI Desk <em>Optional</em></strong><small>Your people, in the conversation</small></span><ArrowForwardRounded /></Link>
    </section>

    <section className="ek-detail-band ek-detail-included" aria-labelledby="ek-detail-included-title"><div><span className="ek-detail-section-label">Designed around your business</span>
      <h2 id="ek-detail-included-title">What comes with your kit</h2><p>{definition.description}</p></div><ul>{definition.features.map(feature => <li key={feature}><CheckRounded />{feature}</li>)}</ul></section>

    <section className="ek-detail-closing"><h2>{copy.closing}</h2><div className="ek-detail-closing-actions">
      <Button component={Link} to={getStartedPath} variant="contained" startIcon={<RocketLaunchOutlined />}>Install kit</Button>
      {demo && <Button component="a" href={demo} target="_blank" rel="noopener noreferrer" startIcon={<PlayCircleOutlineRounded />}>Try the live demo</Button>}
      <IntegrationCallButton variant="outlined">Start a project</IntegrationCallButton>
    </div><p>Start a project with an integration call. No Console account is needed to book.</p><p>{copy.disclosure}</p><Link className="ek-detail-back" to="/experiences"><ArrowBackRounded />Back to all experiences</Link></section>
  </article>
}
