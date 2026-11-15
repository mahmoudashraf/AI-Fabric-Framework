import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Box, Button, ButtonBase, CircularProgress, Divider, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import CloseRounded from '@mui/icons-material/CloseRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import LinkRounded from '@mui/icons-material/LinkRounded'
import TuneRounded from '@mui/icons-material/TuneRounded'
import ExpandMoreRounded from '@mui/icons-material/ExpandMoreRounded'
import HistoryRounded from '@mui/icons-material/HistoryRounded'
import type { AIWorkspaceInstallationSummary, DeploymentReleaseSummary, DeploymentTargetProfileSummary } from '../../api/platformApi'
import { BuildWithAIButton } from '../../assistant/BuildWithAIButton'
import { brainPath } from '../../brain/brainNavigation'
import { brainLibraryAppState, brainLibraryEnvironment, brainLibraryReleaseLabel, type BrainLibraryState, type BrainReleaseEvidence, type CustomerResource } from './customerResourceModels'

const setupTasks = [
  { label: 'Add your documents', description: 'Policies, guides and product information', path: 'knowledge/documents', Icon: DescriptionOutlined, color: 'primary.main' },
  { label: 'Connect your tools', description: 'Bring your APIs into the conversation', path: 'capabilities', Icon: LinkRounded, color: 'secondary.main' },
  { label: 'Shape its answers', description: 'Tone, guidance and useful next steps', path: 'responses', Icon: TuneRounded, color: 'warning.main' },
] as const

export function BrainLibraryInspector({ resource, state, targetProfileId, apps, releases, profiles, evidence, profileEvidence, resourcesAvailable,
  selectedReleaseId, notices, technicalDetails, onBuild, onClose, onInspectApp }: {
  resource: CustomerResource
  state: BrainLibraryState
  targetProfileId?: string | null
  apps: AIWorkspaceInstallationSummary[]
  releases: DeploymentReleaseSummary[]
  profiles: DeploymentTargetProfileSummary[]
  evidence: BrainReleaseEvidence
  profileEvidence: BrainReleaseEvidence
  resourcesAvailable: boolean
  selectedReleaseId: string | null
  notices: ReactNode
  technicalDetails: ReactNode
  onBuild: () => void
  onClose: () => void
  onInspectApp: (id: string) => void
}) {
  const connected = apps.filter(app => app.deploymentId === resource.id)
  const connectPath = `/ai-workspaces?${new URLSearchParams({ view: 'manage', connectDeploymentId: resource.id })}`
  const staging = profileEvidence === 'ready' ? profiles.filter(profile => profile.active && profile.environmentName === 'staging') : []
  const journey = [
    { label: 'Build your Brain', href: brainPath(resource.id, '', targetProfileId) },
    { label: 'Try in staging', href: staging.length === 1 ? brainPath(resource.id, 'try', staging[0].id)
      : `/deployments?${new URLSearchParams({ view: 'setup', deploymentId: resource.id, step: 'staging' })}` },
    { label: 'Connect an Engage app', href: connectPath },
    { label: 'Publish when ready', href: brainPath(resource.id, 'updates', targetProfileId) },
  ]
  return <Box component="aside" aria-label={`${resource.name} details`} sx={{ bgcolor: 'background.paper', height: '100%', overflowY: 'auto', minWidth: 0,
    letterSpacing: 0, '& .MuiTypography-root, & .MuiButton-root': { letterSpacing: 0 } }}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ px: { xs: 2, sm: 3 }, pt: 2 }}>
      <Typography variant="overline" color="text.secondary">Smart Brain</Typography>
      <Tooltip title="Close details"><IconButton aria-label="Close resource details" onClick={onClose}><CloseRounded fontSize="small" /></IconButton></Tooltip>
    </Stack>
    <Stack spacing={3} sx={{ px: { xs: 2, sm: 3 }, pt: 0.5, pb: 3 }}>
      <Box>
        <Typography variant="h5" component="h2" fontWeight={700} sx={{ fontSize: 24, overflowWrap: 'anywhere' }}>{resource.name}</Typography>
        <Stack direction="row" gap={1} alignItems="center" sx={{ mt: 1 }}>
          <Box component="span" sx={{ width: 8, height: 8, borderRadius: '50%', flexShrink: 0, bgcolor: state.color === 'default' ? 'text.secondary' : `${state.color}.main` }} />
          <Typography variant="body2" color="text.secondary">{state.live ? state.liveLabel : `${state.label} · ${state.liveLabel}`}</Typography>
        </Stack>
      </Box>
      {notices}
      <BuildWithAIButton size="large" fullWidth onClick={onBuild} sx={{ minHeight: 48, fontSize: 16 }} />
      <Box component="section" aria-labelledby="brain-next-step">
        <Typography variant="body2" color="text.secondary" sx={{ mb: 0.75 }}>{state.draft ? 'Your next step' : 'Keep improving your AI'}</Typography>
        <Typography id="brain-next-step" component="h3" variant="h5" fontWeight={700} sx={{ mb: 2, fontSize: 24 }}>{state.draft ? 'Give your AI something to know.' : 'What would you like to improve?'}</Typography>
        <Stack component="nav" aria-label="Brain setup guide" divider={<Divider />}>
          {setupTasks.map(({ label, description, path, Icon, color }) => <ButtonBase key={path} component={RouterLink} to={brainPath(resource.id, path, targetProfileId)}
            sx={{ display: 'grid', gridTemplateColumns: '32px minmax(0,1fr) 20px', gap: 1.5, textAlign: 'left', py: 1.5, px: 1, borderRadius: '4px',
              '&:hover': { bgcolor: 'action.hover' }, '&.Mui-focusVisible': { outline: '2px solid', outlineColor: 'primary.main' } }}>
            <Icon sx={{ color, fontSize: 28 }} /><Box sx={{ minWidth: 0 }}><Typography fontWeight={650} sx={{ fontSize: 16 }}>{label}</Typography><Typography variant="body2" color="text.secondary" sx={{ fontSize: 14 }}>{description}</Typography></Box><ArrowForwardRounded sx={{ fontSize: 20 }} />
          </ButtonBase>)}
        </Stack>
      </Box>
      <Divider />
      <Box component="section" aria-labelledby="brain-customer-steps">
        <Typography id="brain-customer-steps" component="h3" variant="h6" sx={{ mb: 1, fontSize: 18 }}>From setup to customers</Typography>
        <Stack component="ol" sx={{ listStyle: 'none', p: 0, m: 0 }}>
          {journey.map(({ label, href }, index) => <Box component="li" key={label} sx={{ position: 'relative', '&:not(:last-child):before': { content: '""', position: 'absolute', left: 15, top: 32, bottom: -8, borderLeft: 1, borderColor: 'divider' } }}>
            <Button component={RouterLink} to={href} sx={{ width: '100%', justifyContent: 'flex-start', gap: 2, py: 0.75, px: 0, color: 'text.primary', textAlign: 'left', fontSize: 15, fontWeight: index === 0 ? 650 : 400 }}>
              <Box component="span" sx={{ width: 32, height: 32, flexShrink: 0, display: 'grid', placeItems: 'center', borderRadius: '50%', border: 1, borderColor: index === 0 ? 'primary.main' : 'divider',
                color: index === 0 ? 'primary.contrastText' : 'text.primary', bgcolor: index === 0 ? 'primary.main' : theme => alpha(theme.palette.text.primary, 0.035) }}>{index + 1}</Box>{label}
            </Button>
          </Box>)}
        </Stack>
      </Box>
      {connected.length || !resourcesAvailable ? <Box component="section" aria-label="Connected Engage apps">
        <Typography fontWeight={650} sx={{ mb: 1 }}>Connected Engage apps</Typography>
        {!resourcesAvailable ? <Typography variant="body2" color="text.secondary">App details unavailable.</Typography> : null}
        <Stack spacing={1}>{connected.map(app => {
          const release = releases.find(item => item.id === app.releaseId && item.deploymentId === resource.id)
          const environment = release && profileEvidence === 'ready' ? brainLibraryEnvironment(release, profiles) : 'Environment unavailable'
          const appState = brainLibraryAppState(app, releases, profiles, evidence, profileEvidence)
          return <Box key={app.installationId}>
            <Button onClick={() => onInspectApp(app.installationId)} endIcon={<ArrowForwardRounded />} sx={{ px: 0, textAlign: 'left', overflowWrap: 'anywhere' }}>{app.displayName}</Button>
            <Typography variant="caption" color="text.secondary" display="block">{resourcesAvailable ? appState.label : 'App status unavailable'}
              {' · '}{!app.releaseId ? 'No release assigned' : evidence === 'loading' ? 'Loading assigned release' : evidence !== 'ready' || !release ? 'Assigned release unavailable'
                : `${environment} · ${brainLibraryReleaseLabel(release)}`}</Typography>
          </Box>
        })}</Stack>
      </Box> : null}
      {evidence === 'loading' ? <Stack direction="row" gap={1} alignItems="center"><CircularProgress size={16} /><Typography variant="body2" color="text.secondary">Loading release details...</Typography></Stack> : null}
      {selectedReleaseId ? <Typography variant="body2" color="text.secondary">A selected release is shown in Technical details.</Typography> : null}
      <Divider />
      <Box sx={{ display: 'flex', alignItems: 'flex-start', gap: 1, flexWrap: 'wrap' }}>
        <Box component="details" sx={{ flex: '1 1 150px', minWidth: 0, '&[open]': { flexBasis: '100%' }, '&[open] .brain-details-chevron': { transform: 'rotate(180deg)' } }}>
          <Box component="summary" sx={{ display: 'flex', alignItems: 'center', gap: 1, cursor: 'pointer', minHeight: 34, fontWeight: 650, listStyle: 'none', '&::-webkit-details-marker': { display: 'none' } }}>
            <ExpandMoreRounded className="brain-details-chevron" fontSize="small" />Technical details
          </Box>
          <Stack spacing={2.5} sx={{ pt: 2 }}>{technicalDetails}</Stack>
        </Box>
        <Button component={RouterLink} to={brainPath(resource.id, 'updates', targetProfileId)} startIcon={<HistoryRounded />} sx={{ whiteSpace: 'normal', alignSelf: 'flex-start', ml: 'auto' }}>Versions &amp; updates</Button>
      </Box>
    </Stack>
  </Box>
}
