import type { ReactNode } from 'react'
import { Alert, Box, Button, Chip, CircularProgress, Stack, Typography } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import CheckRounded from '@mui/icons-material/CheckRounded'
import HubOutlined from '@mui/icons-material/HubOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import CableOutlined from '@mui/icons-material/CableOutlined'
import ChatBubbleOutlineRounded from '@mui/icons-material/ChatBubbleOutlineRounded'
import ScienceOutlined from '@mui/icons-material/ScienceOutlined'
import RocketLaunchOutlined from '@mui/icons-material/RocketLaunchOutlined'
import { Link as RouterLink } from 'react-router-dom'
import type { CustomerDeploymentSummary, CustomerOfferSummary } from '../../api/platformApi'
import { brainPath } from '../../brain/brainNavigation'
import { deploymentTerm } from '../../deploymentTerminology'

export const customerSetupStages = [
  { id: 'template', label: 'Starting point', description: 'Choose what your AI will help customers do.' },
  { id: 'configure', label: 'Connect your business', description: 'Connect the information and services your AI needs.' },
  { id: 'staging', label: 'Try in staging', description: 'Test your AI in a separate environment before customers use it.' },
  { id: 'production', label: 'Go live', description: 'Review your tested setup and choose when customers can use it.' },
  { id: 'retention', label: 'Manage staging', description: 'Decide whether to keep your test environment after going live.' },
] as const
export type CustomerSetupStage = typeof customerSetupStages[number]['id']

export function setupTemplateOutcomes(offer: CustomerOfferSummary | null) {
  if (!offer) return []
  const outcomes: { id: string; title: string; description: string }[] = []
  if (offer.bindingSlots.some(slot => slot.kind === 'CUSTOMER_S3')) outcomes.push({
    id: 'documents', title: 'Answers from your knowledge', description: 'Connect approved documents and guides.' })
  if (offer.bindingSlots.some(slot => slot.kind === 'EXTERNAL_SERVICE')) outcomes.push({
    id: 'systems', title: 'Connected to your business', description: 'Bring in live information and available services.' })
  if (offer.workspaceProfiles.length) outcomes.push({
    id: 'workspace', title: 'A customer-facing AI experience', description: 'Connect a branded Engage workspace when ready.' })
  return outcomes
}

const outcomeIcons = { documents: DescriptionOutlined, systems: CableOutlined, workspace: ChatBubbleOutlineRounded }

export function CustomerBrainSetupHeader({ step, hasDeployment, onStep }: {
  step: CustomerSetupStage; hasDeployment: boolean; onStep: (step: CustomerSetupStage) => void
}) {
  return <Box component="header" sx={{ minWidth: 0 }}>
    <Button component={RouterLink} to="/deployments" startIcon={<ArrowBackRounded />} size="small" sx={{ px: 0, mb: 1.5 }}>
      All {deploymentTerm.pluralLowercase}
    </Button>
    <Typography component="h1" sx={{ fontSize: 26, fontWeight: 650, lineHeight: 1.25 }}>Get your AI ready for customers</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75 }}>{step === 'template' && hasDeployment
      ? 'Review your starting point, then continue with your business connections.'
      : customerSetupStages.find(item => item.id === step)?.description}</Typography>
    <Box component="nav" aria-label={`${deploymentTerm.singular} setup stages`} sx={{ display: 'flex', mt: 3, borderBottom: 1, borderColor: 'divider', overflowX: 'auto' }}>
      {customerSetupStages.map((item, index) => <Button key={item.id} aria-current={item.id === step ? 'step' : undefined}
        disabled={item.id !== 'template' && !hasDeployment} onClick={() => onStep(item.id)}
        sx={{ flex: '1 0 auto', gap: 1, justifyContent: 'flex-start', py: 1.5, px: { xs: 1, md: 1.5 }, borderRadius: 0,
          borderBottom: 2, borderColor: item.id === step ? 'primary.main' : 'transparent',
          color: item.id === step ? 'primary.main' : 'text.secondary', fontSize: 12 }}>
        <Box component="span" sx={{ width: 22, height: 22, flexShrink: 0, borderRadius: '50%', border: 1,
          borderColor: item.id === step ? 'primary.main' : 'divider', display: 'grid', placeItems: 'center', fontSize: 11 }}>{index + 1}</Box>
        {item.label}
      </Button>)}
    </Box>
  </Box>
}

type StartingPointProps = {
  deployment: CustomerDeploymentSummary | null
  offer: CustomerOfferSummary | null
  offers: CustomerOfferSummary[]
  selectedOfferId: string
  onOffer: (id: string) => void
  onContinue: () => void
  onCreateAnother: () => void
  creationFields: ReactNode
  creationNotices: ReactNode
  creationDetails: ReactNode
  workspaceTargetProfileId?: string | null
  canCreate: boolean
  creating: boolean
  onCreate: () => void
}

export function CustomerBrainStartingPoint(props: StartingPointProps) {
  const { deployment, offer } = props
  const outcomes = setupTemplateOutcomes(offer)
  return <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1fr) 265px' }, gap: { xs: 3, lg: 4 }, minWidth: 0 }}>
    <Box component="section" aria-label="Your starting point" sx={{ minWidth: 0 }}>
      {deployment ? <>
        <Stack direction="row" alignItems="center" spacing={1} sx={{ mb: 1.5 }}><CheckRounded sx={{ color: 'success.main', fontSize: 18 }} />
          <Typography variant="body2" color="text.secondary">Your starting point is saved</Typography></Stack>
        <Stack direction="row" spacing={1.5} alignItems="center">
          <Box sx={{ width: 44, height: 44, flexShrink: 0, border: 1, borderColor: 'divider', borderRadius: 1, display: 'grid', placeItems: 'center', bgcolor: 'action.hover' }}><HubOutlined color="primary" /></Box>
          <Box sx={{ minWidth: 0 }}><Typography component="h2" sx={{ fontSize: 22, fontWeight: 650, overflowWrap: 'anywhere' }}>{deployment.name}</Typography>
            <Typography variant="body2" color="text.secondary">{offer ? `Started from ${offer.name}` : 'Starting template details are unavailable.'}</Typography></Box>
        </Stack>
        <Typography variant="body2" sx={{ mt: 2.5, maxWidth: 650 }} color="text.secondary">
          Review your connections and test changes before going live. Your saved configuration stays in place.
        </Typography>
      </> : <>
        <Typography component="h2" sx={{ fontSize: 20, fontWeight: 650 }}>Choose your starting point</Typography>
        <Typography variant="body2" color="text.secondary" sx={{ mt: 0.75, mb: 2 }}>Start with a template, then make the setup your own.</Typography>
        <Box role="group" aria-label="Available starting templates" sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: 'repeat(2, minmax(0, 1fr))' }, gap: 1, maxHeight: 270, overflow: 'auto', overscrollBehavior: 'contain' }}>
          {props.offers.map(item => <Button key={item.id} aria-pressed={props.selectedOfferId === item.id} onClick={() => props.onOffer(item.id)}
            variant="outlined" sx={{ width: '100%', minHeight: 54, textAlign: 'left', justifyContent: 'flex-start', gap: 1.5, px: 1.5,
              borderColor: props.selectedOfferId === item.id ? 'primary.main' : 'divider', color: 'text.primary',
              bgcolor: props.selectedOfferId === item.id ? 'action.selected' : 'transparent' }}>
            <HubOutlined sx={{ color: props.selectedOfferId === item.id ? 'primary.main' : 'text.secondary', fontSize: 20 }} />
            <Typography component="span" variant="body2" sx={{ flex: 1, overflowWrap: 'anywhere' }}>{item.name}</Typography>
            {props.selectedOfferId === item.id && <CheckRounded color="primary" sx={{ fontSize: 18 }} />}
          </Button>)}
        </Box>
        {!props.offers.length && <Alert severity="info">No starting template is available for this workspace yet.</Alert>}
      </>}

      {outcomes.length > 0 && <Box component="ul" aria-label="What this starting point can help you build" sx={{ listStyle: 'none', p: 0, my: 3,
        display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', md: deployment ? 'minmax(0, 1fr)' : 'repeat(3, minmax(0, 1fr))' }, gap: deployment ? 0 : 2 }}>
        {outcomes.map(item => {
          const Icon = outcomeIcons[item.id as keyof typeof outcomeIcons]
          return <Stack component="li" direction="row" spacing={1.5} key={item.id} sx={{ py: 1.25, borderBottom: 1, borderColor: 'divider' }}>
            <Icon sx={{ fontSize: 20, color: 'primary.main', mt: 0.25 }} />
            <Box><Typography variant="body2" fontWeight={600}>{item.title}</Typography><Typography variant="body2" color="text.secondary">{item.description}</Typography></Box>
          </Stack>
        })}
      </Box>}

      {!deployment && <Box sx={{ mt: 3 }}>{props.creationFields}{props.creationNotices}
        <Box component="details" sx={{ mt: 2 }}><Typography component="summary" variant="body2" color="text.secondary" sx={{ cursor: 'pointer' }}>Template and target references</Typography>
          <Box sx={{ mt: 1.5 }}>{props.creationDetails}</Box></Box></Box>}
      <Stack direction={{ xs: 'column', sm: 'row' }} gap={1.5} alignItems={{ sm: 'center' }} sx={{ mt: 3, pt: 2.5, borderTop: 1, borderColor: 'divider' }}>
        {deployment ? <>
          <Button variant="contained" endIcon={<ArrowForwardRounded />} onClick={props.onContinue}>Continue setup</Button>
          <Button component={RouterLink} to={brainPath(deployment.deploymentId, '', props.workspaceTargetProfileId ?? deployment.bootstrapTargetProfileId)}>Open {deploymentTerm.lowercase}</Button>
          <Button size="small" onClick={props.onCreateAnother} sx={{ ml: { sm: 'auto' } }}>Create another</Button>
        </> : <Button variant="contained" endIcon={props.creating ? <CircularProgress size={16} color="inherit" /> : <ArrowForwardRounded />}
          disabled={!props.canCreate || props.creating} onClick={props.onCreate}>{props.creating ? 'Creating your setup...' : 'Create my setup'}</Button>}
      </Stack>
      {!deployment && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1.25 }}>This saves your configuration. Running environments are created later, when you publish and apply.</Typography>}
    </Box>
    <Box component="aside" aria-label="Your path to customers" sx={{ borderLeft: { lg: 1 }, borderTop: { xs: 1, lg: 0 }, borderColor: 'divider', pl: { lg: 3 }, pt: { xs: 2.5, lg: 0 } }}>
      <Chip size="small" label="Start in staging" variant="outlined" sx={{ mb: 2 }} />
      <Typography component="h2" variant="subtitle1" fontWeight={600}>Your path to customers</Typography>
      <Stack spacing={2.5} sx={{ mt: 2.5 }}>
        {[
          { Icon: CableOutlined, title: 'Connect your business', text: 'Add your knowledge and connect the services your AI can use.' },
          { Icon: ScienceOutlined, title: 'Try it safely', text: 'Review real answers and actions in your staging environment.' },
          { Icon: RocketLaunchOutlined, title: 'Choose when to go live', text: 'Promote the tested version and connect your customer app.' },
        ].map(item => <Stack direction="row" key={item.title} spacing={1.25}><item.Icon sx={{ color: 'text.secondary', fontSize: 20, mt: 0.25 }} />
          <Box><Typography variant="body2" fontWeight={600}>{item.title}</Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>{item.text}</Typography></Box></Stack>)}
      </Stack>
      <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 3, pt: 2, borderTop: 1, borderColor: 'divider' }}>
        Staging and production are separate environments. Nothing switches your live app automatically.
      </Typography>
    </Box>
  </Box>
}
