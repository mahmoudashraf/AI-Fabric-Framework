import { useEffect } from 'react'
import { Alert, Button, CircularProgress, useTheme } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckRounded from '@mui/icons-material/CheckRounded'
import PlayCircleOutlineRounded from '@mui/icons-material/PlayCircleOutlineRounded'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { CustomerConsoleTheme } from '../theme/CustomerConsoleTheme'
import { experienceKitKeys, fetchPublicExperienceKits, type ExperienceKitDefinition } from '../api/experienceKitsApi'
import { KitChooser } from '../components/kits/KitChooser'
import { KitDeskMark, KitMark, KitProductLine, KitProductLinks, KitPublicHeader } from '../components/kits/KitIdentity'
import { KitCapabilityIllustration, KitExperiencePreview } from '../components/kits/KitCapabilityPreview'
import { KitDealershipOffer } from '../components/kits/KitCommercialPresentation'
import { KitIntegrationJourney } from '../components/kits/KitIntegrationJourney'
import { kitPreviewKind, safeKitExternalUrl } from '../components/kits/kitModels'
import './ExperienceKits.css'

export type ExperienceKitsPublicPageProps = { code?: string; authenticated?: boolean; consolePath?: string }

function KitPublicDetail({ definition, authenticated }: { definition: ExperienceKitDefinition; authenticated: boolean }) {
  const dealership = definition.code === 'dealership-ai-experience'
  const support = definition.code === 'ai-support-experience'
  const kind = kitPreviewKind(definition.code)
  const demo = safeKitExternalUrl(definition.demoUrl)
  const getStartedPath = authenticated ? `/kits/new/${definition.code}` : `/sign-up?kit=${definition.code}`
  return <>
    <Button component={Link} to="/experiences" startIcon={<ArrowBackRounded />} className="ek-back">Back to experiences</Button>
    <section className="ek-public-detail ek-detail-refresh" aria-labelledby="ek-detail-title">
      <div className="ek-detail-title"><KitMark product={dealership ? 'dealership' : 'engage'} large /><div><h1 id="ek-detail-title">{definition.name}</h1><p>{definition.summary}</p><KitProductLine dealership={dealership} /></div></div>
      <div className="ek-public-detail-grid"><div><h2>Your customer experience</h2><KitCapabilityIllustration kind={kind} />
        <KitExperiencePreview kind={kind} name={definition.name} /></div>
        <div className="ek-detail-copy"><h2>{support ? 'Help customers solve problems and follow up.' : dealership ? 'Help buyers move from browsing to an enquiry.' : 'Make it easier to do business with you.'}</h2>
          <p className="ek-muted">{support ? 'Approved guidance, conversation-linked requests and optional human support.' : dealership ? 'For dealerships that want to help buyers choose with confidence.' : 'For businesses that want to help customers understand their services and take the next step.'}</p><p>{definition.description}</p>
          <ul>{definition.features.map(feature => <li key={feature}><CheckRounded />{feature}</li>)}</ul>
          {dealership && <KitDealershipOffer />}
          <div className="ek-detail-desk"><KitDeskMark /><div><strong>{support ? 'Request tracking and human support are separate choices' : 'Keep your team within reach'}</strong><p>{support ? 'Use Desk-managed tickets without turning on human takeover. Add your own agents separately when needed.' : 'Add your own support agents with LoomAI Desk. They can pick up the conversation when a customer needs a person.'}</p></div></div>
          <div className="ek-detail-actions">
            <Button component={Link} variant="contained" endIcon={<ArrowForwardRounded />} className="ek-primary-button"
              to={getStartedPath}>Get started</Button>
            {demo && <Button component="a" href={demo} target="_blank" rel="noopener noreferrer" startIcon={<PlayCircleOutlineRounded />}>Try demo</Button>}
          </div>
          {dealership && <p className="ek-muted">The available dealership demo uses fictional inventory and a simulated AutoTrader integration.</p>}
        </div>
      </div>
      <KitIntegrationJourney kind={kind} getStartedPath={getStartedPath} />
    </section>
  </>
}

function ExperienceKitsPublicContent({ code: selectedCode, authenticated: authenticatedProp, consolePath }: ExperienceKitsPublicPageProps) {
  const auth = usePlatformAuth()
  const authenticated = authenticatedProp ?? Boolean(auth.session?.authenticated && auth.session.customerId)
  const { palette } = useTheme()
  const catalogue = useQuery({ queryKey: experienceKitKeys.public, queryFn: ({ signal }) => fetchPublicExperienceKits(signal), staleTime: 60_000, retry: false })
  const definition = catalogue.data?.find(item => item.code === selectedCode)
  useEffect(() => { window.scrollTo(0, 0) }, [selectedCode])
  return <div className={`ek-page ek-public-page${!selectedCode ? ' ek-catalogue-page' : ''}`} data-kit-theme={palette.mode}>
    <div className="ek-public-container"><KitPublicHeader authenticated={authenticated} selectedCode={selectedCode} consolePath={consolePath} catalogue={!selectedCode} />
      <main>
        {catalogue.isPending && <div className="ek-query-state" role="status"><CircularProgress size={26} /><span>Loading experiences...</span></div>}
        {catalogue.isError && <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{catalogue.error instanceof Error ? catalogue.error.message : 'Experiences could not be loaded.'}</Alert>}
        {catalogue.isSuccess && (selectedCode ? definition ? <KitPublicDetail definition={definition} authenticated={authenticated} />
          : <Alert severity="info" action={<Button component={Link} to="/experiences">Back to experiences</Button>}>This experience is not available.</Alert>
          : catalogue.data.length ? <KitChooser definitions={catalogue.data} authenticated={authenticated} />
            : <Alert severity="info">No experiences are currently available.</Alert>)}
        <div className="ek-integration-help"><span>Need a hand getting started?</span><Link to="/support">Get setup help <ArrowForwardRounded /></Link></div>
        <KitProductLinks />
      </main>
      <footer className="ek-public-footer">One account. Your connected AI products.</footer>
    </div>
  </div>
}

export function ExperienceKitsPublicPage(props: ExperienceKitsPublicPageProps) {
  return <CustomerConsoleTheme><ExperienceKitsPublicContent {...props} /></CustomerConsoleTheme>
}
