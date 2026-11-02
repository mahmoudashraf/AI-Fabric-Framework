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
import { KitMark, KitProductLine, KitProductLinks, KitPublicHeader, KitSupportBand } from '../components/kits/KitIdentity'
import { KitWorkspacePreview } from '../components/kits/KitWorkspacePreview'
import { safeKitExternalUrl } from '../components/kits/kitModels'
import './ExperienceKits.css'

export type ExperienceKitsPublicPageProps = { code?: string; authenticated?: boolean; consolePath?: string }

function KitPublicDetail({ definition, authenticated }: { definition: ExperienceKitDefinition; authenticated: boolean }) {
  const dealership = definition.code === 'dealership-ai-experience'
  const demo = safeKitExternalUrl(definition.demoUrl)
  return <>
    <Button component={Link} to="/experiences" startIcon={<ArrowBackRounded />} className="ek-back">Back to experiences</Button>
    <section className="ek-public-detail" aria-labelledby="ek-detail-title">
      <div className="ek-detail-title"><KitMark product={dealership ? 'dealership' : 'engage'} large /><div><h1 id="ek-detail-title">{definition.name}</h1><p>{definition.summary}</p><KitProductLine dealership={dealership} /></div></div>
      <div className="ek-public-detail-grid"><div><h2>Your customer experience</h2><KitWorkspacePreview packCode={definition.experiencePackCode}
        workspaceName={definition.defaultWorkspaceName} brandColor={dealership ? '#409aff' : '#28bfa6'} /></div>
        <div className="ek-detail-copy"><h2>{dealership ? 'Help buyers move from browsing to an enquiry.' : 'Make it easier to do business with you.'}</h2>
          <p className="ek-muted">{dealership ? 'For dealerships that want to help buyers choose with confidence.' : 'For businesses that want to help customers understand their services and take the next step.'}</p><p>{definition.description}</p>
          <ul>{definition.features.map(feature => <li key={feature}><CheckRounded />{feature}</li>)}</ul>
          <div className="ek-detail-desk"><KitMark product="desk" /><div><strong>Keep your team within reach</strong><p>Add your own support agents with LoomAI Desk. They can pick up the conversation when a customer needs a person.</p></div></div>
          <Button component={Link} variant="contained" endIcon={<ArrowForwardRounded />} className="ek-primary-button"
            to={authenticated ? `/kits/new/${definition.code}` : `/sign-up?kit=${definition.code}`}>Get started</Button>
          {demo && <Button component="a" href={demo} target="_blank" rel="noopener noreferrer" startIcon={<PlayCircleOutlineRounded />}>Try demo</Button>}
          {dealership && <p className="ek-muted">The available dealership demo uses fictional inventory and a simulated AutoTrader integration.</p>}
        </div>
      </div>
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
  return <div className="ek-page ek-public-page" data-kit-theme={palette.mode}>
    <div className="ek-public-container"><KitPublicHeader authenticated={authenticated} selectedCode={selectedCode} consolePath={consolePath} />
      <main>
        {!selectedCode && <section className="ek-public-intro"><h1>AI experiences for your customers</h1><p>Help people find answers, choose confidently and take the next step with your business.</p></section>}
        {catalogue.isPending && <div className="ek-query-state" role="status"><CircularProgress size={26} /><span>Loading experiences...</span></div>}
        {catalogue.isError && <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{catalogue.error instanceof Error ? catalogue.error.message : 'Experiences could not be loaded.'}</Alert>}
        {catalogue.isSuccess && (selectedCode ? definition ? <KitPublicDetail definition={definition} authenticated={authenticated} />
          : <Alert severity="info" action={<Button component={Link} to="/experiences">Back to experiences</Button>}>This experience is not available.</Alert>
          : catalogue.data.length ? <><KitChooser definitions={catalogue.data} authenticated={authenticated} /><KitSupportBand /></>
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
