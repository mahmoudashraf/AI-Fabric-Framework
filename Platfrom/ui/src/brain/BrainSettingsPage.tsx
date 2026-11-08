import { Alert, Box, Link, Stack } from '@mui/material'
import { Link as RouterLink, Navigate, useLocation } from 'react-router-dom'
import { ProvidersPage } from '../pages/ProvidersPage'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'
import { useBrainWorkspace } from './BrainWorkspaceContext'

import { brainAdvancedSections, legacyBrainSettingsDestination } from './brainNavigation'

export function BrainModelsPage() {
  const { brainId, href } = useBrainWorkspace()
  return <Box sx={{ ...brainPaneSx, flex: 1 }}>
    <BrainPageHeader title="AI models" description="Choose the models that understand requests and generate answers."
      action={<Link component={RouterLink} to={href('advanced/providers')} fontSize={13}>Provider parameters</Link>} />
    <ProvidersPage brainId={brainId} configurationOnly view="models" />
  </Box>
}

export function BrainAdvancedPage() {
  const { brainId, href } = useBrainWorkspace()
  const location = useLocation()
  const parts = location.pathname.split('/').slice(4)
  const selected = brainAdvancedSections.find(item => item.path === parts[0])
  if (!parts.length) return <Navigate replace to={href('advanced/runtime')} />
  if (!selected || parts.length !== 1) return <Alert severity="warning">This advanced page is not available.</Alert>
  return <Box sx={{ ...brainPaneSx, flex: 1 }}>
    <BrainPageHeader title={selected.label} />
    <Stack direction="row" gap={2} flexWrap="wrap" sx={{ mb: 2, pb: 1, borderBottom: 1, borderColor: 'divider' }}>
      <Link component={RouterLink} to={href('capabilities/actions')}>Action JSON &amp; routes</Link>
      <Link component={RouterLink} to={href('responses/advanced')}>Prompt technical details</Link>
      <Link component={RouterLink} to={href('updates')}>Versions &amp; release records</Link>
    </Stack>
    <ProvidersPage brainId={brainId} configurationOnly view="technical-files" technicalSection={selected.view} />
  </Box>
}

// Old bookmarks are adapters only; the editors have a single canonical home.
export function BrainSettingsPage() {
  const { href } = useBrainWorkspace()
  const location = useLocation()
  const destination = legacyBrainSettingsDestination(location.pathname)
  if (!destination) return <Alert severity="warning">This settings section is not available.</Alert>
  const [path, query] = href(destination.path).split('?')
  const next = new URLSearchParams(query)
  if (destination.panel) next.set('panel', destination.panel)
  return <Navigate replace to={`${path}${next.size ? `?${next}` : ''}`} />
}
