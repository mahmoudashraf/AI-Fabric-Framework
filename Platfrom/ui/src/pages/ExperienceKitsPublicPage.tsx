import { useEffect } from 'react'
import { Alert, Button, CircularProgress, useTheme } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { CustomerConsoleTheme } from '../theme/CustomerConsoleTheme'
import { experienceKitKeys, fetchPublicExperienceKits } from '../api/experienceKitsApi'
import { KitChooser } from '../components/kits/KitChooser'
import { KitProductLinks, KitPublicHeader } from '../components/kits/KitIdentity'
import { KitDetailPage } from '../components/kits/KitDetailPage'
import { IntegrationCallButton } from '../components/IntegrationCallButton'
import './ExperienceKits.css'

export type ExperienceKitsPublicPageProps = { code?: string; authenticated?: boolean; consolePath?: string }

function ExperienceKitsPublicContent({ code: selectedCode, authenticated: authenticatedProp, consolePath }: ExperienceKitsPublicPageProps) {
  const auth = usePlatformAuth()
  const authenticated = authenticatedProp ?? Boolean(auth.session?.authenticated && auth.session.customerId)
  const { palette } = useTheme()
  const catalogue = useQuery({ queryKey: experienceKitKeys.public, queryFn: ({ signal }) => fetchPublicExperienceKits(signal), staleTime: 60_000, retry: false })
  const definition = catalogue.data?.find(item => item.code === selectedCode)
  useEffect(() => { window.scrollTo(0, 0) }, [selectedCode])
  return <div className={`ek-page ek-public-page${!selectedCode ? ' ek-catalogue-page' : ' ek-detail-page'}`} data-kit-theme={palette.mode}>
    <div className="ek-public-container"><KitPublicHeader authenticated={authenticated} selectedCode={selectedCode} consolePath={consolePath} catalogue={!selectedCode} />
      <main>
        {catalogue.isPending && <div className="ek-query-state" role="status"><CircularProgress size={26} /><span>Loading experiences...</span></div>}
        {catalogue.isError && <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{catalogue.error instanceof Error ? catalogue.error.message : 'Experiences could not be loaded.'}</Alert>}
        {catalogue.isSuccess && (selectedCode ? definition ? <KitDetailPage definition={definition} authenticated={authenticated} />
          : <Alert severity="info" action={<Button component={Link} to="/experiences">Back to experiences</Button>}>This experience is not available.</Alert>
          : catalogue.data.length ? <KitChooser definitions={catalogue.data} authenticated={authenticated} />
            : <Alert severity="info">No experiences are currently available.</Alert>)}
        <div className="ek-integration-help"><span>Work with our AI integration team.</span><IntegrationCallButton />
          {authenticated && <Link to="/support?new=1">Submit an integration request <ArrowForwardRounded /></Link>}</div>
        {!selectedCode && <KitProductLinks />}
      </main>
      <footer className="ek-public-footer">One account. Your connected AI products.</footer>
    </div>
  </div>
}

export function ExperienceKitsPublicPage(props: ExperienceKitsPublicPageProps) {
  return <CustomerConsoleTheme><ExperienceKitsPublicContent {...props} /></CustomerConsoleTheme>
}
