import { Alert, Button, CircularProgress, useTheme } from '@mui/material'
import AddRounded from '@mui/icons-material/AddRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link, useParams } from 'react-router-dom'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { enrollPublicCustomerTemplates } from '../api/platformApi'
import { experienceKitKeys, fetchExperienceKitCatalogue, fetchExperienceKits } from '../api/experienceKitsApi'
import { KitChooser } from '../components/kits/KitChooser'
import { KitMark } from '../components/kits/KitIdentity'
import { ExperienceKitSetup } from '../components/kits/ExperienceKitSetup'
import { ExperienceKitOverview } from '../components/kits/ExperienceKitOverview'
import './ExperienceKits.css'

export function ExperienceKitsRefreshTemplates({ customerId }: { customerId: string }) {
  const queryClient = useQueryClient()
  const refresh = useMutation({ mutationFn: enrollPublicCustomerTemplates,
    onSuccess: () => queryClient.invalidateQueries({ queryKey: experienceKitKeys.catalogue(customerId) }) })
  return <div className="ek-refresh-templates"><Button startIcon={<RefreshRounded />} disabled={refresh.isPending}
    onClick={() => refresh.mutate()}>{refresh.isPending ? 'Refreshing experiences...' : 'Refresh available experiences'}</Button>
    {refresh.isError && <Alert severity="error">{refresh.error instanceof Error ? refresh.error.message : 'Templates could not be refreshed.'}</Alert>}
  </div>
}

function ExperienceKitsList({ customerId }: { customerId: string }) {
  const kits = useQuery({ queryKey: experienceKitKeys.list(customerId), queryFn: ({ signal }) => fetchExperienceKits(signal), retry: false })
  const catalogue = useQuery({ queryKey: experienceKitKeys.catalogue(customerId), queryFn: ({ signal }) => fetchExperienceKitCatalogue(signal), retry: false })
  return <>
    <div className="ek-section-heading"><div><h1>My kits</h1><p>Help your customers. Make each experience your own.</p></div>
      <Button component={Link} to="/experiences" startIcon={<AddRounded />}>Explore experiences</Button></div>
    {kits.isPending && <div className="ek-query-state" role="status"><CircularProgress size={24} />Loading your kits...</div>}
    {kits.isError && <Alert severity="error" action={<Button onClick={() => void kits.refetch()}>Retry</Button>}>{kits.error instanceof Error ? kits.error.message : 'Your kits could not be loaded.'}</Alert>}
    {kits.isSuccess && kits.data.length > 0 && <div className="ek-saved-kits">{kits.data.map(kit => {
      const definition = catalogue.data?.find(entry => entry.definition.code === kit.kitCode)?.definition
      const missing = !kit.workspace || (kit.deliveryMode === 'both' && !kit.embeddedWorkspace)
      const apps = kit.deliveryMode === 'both' ? [kit.workspace, kit.embeddedWorkspace] : [kit.workspace]
      const active = !missing && apps.every(app => app?.status === 'ACTIVE' && app.ready && app.deploymentId === kit.deploymentId && Boolean(app.releaseId))
      return <article className="ek-saved-kit" key={kit.id}><div className="ek-saved-kit-heading"><KitMark product={kit.kitCode === 'dealership-ai-experience' ? 'dealership' : 'engage'} />
        <span className={`ek-badge${!active ? ' ek-badge-amber' : ''}`}>{missing ? 'Setup needed' : active ? 'Live' : 'Not live yet'}</span></div>
        <h2>{kit.name}</h2><p>{definition?.name ?? kit.kitCode}</p>
        <div className="ek-saved-kit-facts"><span>{kit.deliveryMode === 'both' ? 'Website embed + standalone' : kit.deliveryMode === 'embedded' ? 'Website embed' : 'Standalone app'}</span>
          <span>{kit.humanSupportEnabled ? 'Desk setup requested' : 'Desk optional'}</span></div>
        {missing && <p className="ek-amber">An installation is missing. Open the kit to review its products.</p>}
        <Button component={Link} to={`/kits/${encodeURIComponent(kit.id)}`} endIcon={<ArrowForwardRounded />}>Open kit</Button>
      </article>
    })}</div>}
    {kits.isSuccess && !kits.data.length && <section className="ek-empty"><KitMark product="engage" large /><h2>Start helping your customers</h2><p>Choose an experience, add your information and give it your brand. Test it before you go live.</p></section>}
    <section className="ek-available-kits"><div className="ek-section-heading"><h2>Choose a starting point</h2><ExperienceKitsRefreshTemplates customerId={customerId} /></div>
      {catalogue.isPending && <CircularProgress size={24} aria-label="Loading available kits" />}
      {catalogue.isError && <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{catalogue.error instanceof Error ? catalogue.error.message : 'Available kits could not be loaded.'}</Alert>}
      {catalogue.isSuccess && (catalogue.data.length ? <KitChooser definitions={catalogue.data.map(entry => entry.definition)} authenticated detailLinks={false} />
        : <Alert severity="info">No kit templates are available for this account. Refresh the available templates or request integration help.</Alert>)}
    </section>
  </>
}

/** The host owns authentication gates, Console layout and route registration. */
export function ExperienceKitsPage() {
  const auth = usePlatformAuth()
  const { code, id } = useParams<{ code: string; id: string }>()
  const { palette } = useTheme()
  if (auth.isLoading) return <CircularProgress size={24} aria-label="Loading your account" />
  if (!isCustomerSession(auth.session)) return <Alert severity="warning">Sign in with a customer administrator account to manage kits.<Button component={Link} to={code ? `/sign-in?kit=${encodeURIComponent(code)}` : '/sign-in'}>Sign in</Button></Alert>
  const customerId = auth.session.customerId
  return <div className="ek-page ek-console-page" data-kit-theme={palette.mode}>
    {code ? <ExperienceKitSetup key={`${customerId}:${auth.session.actorId}:${code}`} customerId={customerId} actorId={auth.session.actorId} code={code} />
      : id ? <ExperienceKitOverview key={`${customerId}:${id}`} customerId={customerId} id={id} />
        : <ExperienceKitsList key={customerId} customerId={customerId} />}
  </div>
}
