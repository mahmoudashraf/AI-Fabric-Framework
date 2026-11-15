import type { ReactNode } from 'react'
import { Link as RouterLink } from 'react-router-dom'
import { Alert, Box, Button, Chip, Divider, IconButton, Stack, Tooltip, Typography } from '@mui/material'
import CloseRounded from '@mui/icons-material/CloseRounded'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import LinkRounded from '@mui/icons-material/LinkRounded'
import PaletteOutlined from '@mui/icons-material/PaletteOutlined'
import LanguageRounded from '@mui/icons-material/LanguageRounded'
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import OpenInNewRounded from '@mui/icons-material/OpenInNewRounded'
import type { AIWorkspaceInstallationSummary } from '../../api/platformApi'
import { engageCheckStep, engageNextStep, engageState, engageStepPath, type EngageStep } from './engagePresentation'
import { safeCustomerAppUrl } from './customerResourceModels'

export function EngageInspector({ app, source, navigation, notices, technicalDetails, onClose }: {
  app: AIWorkspaceInstallationSummary; source: URLSearchParams; navigation?: ReactNode; notices?: ReactNode; technicalDetails: ReactNode; onClose: () => void
}) {
  const state = engageState(app), next = engageNextStep(app), embedded = app.deliveryMode === 'embedded'
  const steps = [
    { id: 'connection', label: 'Connect your Brain', Icon: LinkRounded, description: app.releaseId ? 'Review the assigned AI release' : 'Choose a tested AI release' },
    { id: 'appearance', label: 'Brand your workspace', Icon: PaletteOutlined, description: 'Name, logo, colors and customer preview' },
    { id: 'delivery', label: embedded ? 'Install on your website' : 'Set up your app address', Icon: LanguageRounded, description: embedded ? 'Allowed websites and installation script' : 'Your address, domain ownership and HTTPS' },
    { id: 'readiness', label: 'Check and activate', Icon: FactCheckOutlined, description: app.status === 'ACTIVE' ? 'Review current checks and app controls' : 'Review checks, then choose when to activate' },
  ] satisfies Array<{ id: EngageStep; label: string; Icon: typeof LinkRounded; description: string }>
  const commands = { connection: 'Connect your Brain', appearance: 'Customize your workspace', delivery: embedded ? 'Set up website installation' : 'Set up app address', readiness: 'Review launch checks' }
  const outstanding = app.readinessChecks.filter(check => check.status !== 'PASSED')
  const appUrl = safeCustomerAppUrl(app.appUrl)
  const alternate = new URLSearchParams({ view: 'manage', sourceInstallationId: app.installationId, delivery: embedded ? 'standalone' : 'embedded' })
  for (const key of ['kitId', 'fromBrainId', 'fromBrainReleaseId']) { const value = source.get(key); if (value) alternate.set(key, value) }
  return <Box component="aside" aria-label={`${app.displayName} details`} sx={{ bgcolor: 'background.paper', height: '100%', overflowY: 'auto', minWidth: 0 }}>
    <Stack direction="row" alignItems="center" justifyContent="space-between" gap={1} sx={{ px: 3, pt: 2 }}>
      {navigation ?? <Typography variant="overline" color="text.secondary">Engage app</Typography>}
      <Tooltip title="Close details"><IconButton aria-label="Close resource details" onClick={onClose}><CloseRounded fontSize="small" /></IconButton></Tooltip>
    </Stack>
    <Stack spacing={2.5} sx={{ p: 3, pt: 1 }}>
      <Box><Typography component="h2" fontSize={24} fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>{app.displayName}</Typography>
        <Stack direction="row" alignItems="center" gap={1} sx={{ mt: 1 }}><Chip size="small" label={state.label} color={state.color} /><Typography variant="body2" color="text.secondary">{embedded ? 'Website widget' : 'Standalone app'}</Typography></Stack>
      </Box>
      {notices}
      <Box><Typography variant="body2" color="text.secondary" sx={{ mb: 1 }}>{state.label === 'Active' ? 'Your customer experience' : 'Your next step'}</Typography>
        <Button fullWidth variant="contained" component={RouterLink} to={engageStepPath(app, next, source)} endIcon={<ArrowForwardRounded />} sx={{ minHeight: 46 }}>{commands[next]}</Button>
      </Box>
      {!app.ready && !outstanding.length && <Alert severity="info">Readiness has not been confirmed. Review the app checks before activation.</Alert>}
      {app.domains.filter(domain => domain.lastVerificationError).map(domain => <Alert key={domain.hostname} severity="warning" sx={{ overflowWrap: 'anywhere' }}>{domain.hostname}: {domain.lastVerificationError}</Alert>)}
      <Stack component="nav" aria-label="Engage setup steps" divider={<Divider />}>
        {steps.map(({ id, label, description, Icon }, index) => <Box key={id}><Button component={RouterLink} to={engageStepPath(app, id, source)}
          sx={{ justifyContent: 'flex-start', gap: 1.5, py: 1.5, px: .5, color: 'text.primary', textAlign: 'left' }}>
          <Icon sx={{ color: id === 'connection' ? 'primary.main' : id === 'appearance' ? 'secondary.main' : id === 'delivery' ? 'warning.main' : 'text.secondary' }} />
          <Box sx={{ flex: 1, minWidth: 0 }}><Typography fontSize={15} fontWeight={650}>{index + 1}. {label}</Typography><Typography variant="body2" color="text.secondary" sx={{ fontSize: 13 }}>{description}</Typography></Box><ArrowForwardRounded fontSize="small" />
        </Button>{outstanding.filter(check => engageCheckStep(check) === id).map((check, checkIndex) => <Typography key={`${check.code}:${checkIndex}`} variant="body2" sx={{ ml: 4.75, mb: 1, pl: 1.25, borderLeft: 2, borderColor: check.status === 'BLOCKED' ? 'warning.main' : 'divider', overflowWrap: 'anywhere' }}>{check.message}</Typography>)}</Box>)}
      </Stack>
      <Button component={RouterLink} to={`/ai-workspaces?${alternate}`} startIcon={embedded ? <OpenInNewRounded /> : <LanguageRounded />}>{embedded ? 'Add standalone app' : 'Add website widget'}</Button>
      {app.status === 'ACTIVE' && appUrl && <Button component="a" href={appUrl} target="_blank" rel="noopener noreferrer" startIcon={<OpenInNewRounded />}>Open app</Button>}
      <Divider />
      <Box component="details"><Typography component="summary" fontWeight={650} sx={{ cursor: 'pointer', minHeight: 34 }}>Technical details</Typography><Stack spacing={2.5} sx={{ pt: 2 }}>{technicalDetails}</Stack></Box>
    </Stack>
  </Box>
}
