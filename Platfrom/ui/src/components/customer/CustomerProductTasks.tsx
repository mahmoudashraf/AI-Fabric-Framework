import { Link as RouterLink } from 'react-router-dom'
import { Box, Button, MenuItem, Stack, TextField, Typography } from '@mui/material'
import SettingsOutlined from '@mui/icons-material/SettingsOutlined'
import CableOutlined from '@mui/icons-material/CableOutlined'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import ForumOutlined from '@mui/icons-material/ForumOutlined'
import RocketLaunchOutlined from '@mui/icons-material/RocketLaunchOutlined'
import type { CustomerOfferSummary, DeploymentReleaseSummary } from '../../api/platformApi'
import { releaseEnvironment } from './customerResourceModels'

export function CustomerProductTasks({ deploymentId, offer, releases, selectedReleaseId, onSelectRelease }: {
  deploymentId: string
  offer: CustomerOfferSummary
  releases: DeploymentReleaseSummary[]
  selectedReleaseId: string | null
  onSelectRelease: (id: string) => void
}) {
  const selected = releases.find(release => release.id === selectedReleaseId)
  const scoped = selected?.targetProfileId && selected.provisioningStatus === 'ACTIVE'
    ? new URLSearchParams({ deploymentId, releaseId: selected.id }).toString() : null
  const setup = (step: string) => `/deployments?${new URLSearchParams({ view: 'setup', deploymentId, step })}`
  const configure = (path: string) => `${path}?${new URLSearchParams({ deploymentId, view: 'manage' })}`
  const kinds = new Set(offer.bindingSlots.map(slot => slot.kind))
  return <Box component="section" aria-label="Product setup and operations">
    <Typography fontWeight={600} sx={{ mb: 1 }}>Product setup</Typography>
    <Stack alignItems="flex-start" spacing={0.5}>
      <Button component={RouterLink} to={setup('configure')} startIcon={<SettingsOutlined />}>Continue setup</Button>
      {kinds.has('EXTERNAL_SERVICE') ? <Button component={RouterLink} to={configure('/integrations')} startIcon={<CableOutlined />}>Configure data connection</Button> : null}
      {kinds.has('CUSTOMER_S3') ? <Button component={RouterLink} to={configure('/document-knowledge')} startIcon={<DescriptionOutlined />}>Connect document storage</Button> : null}
      <Button component={RouterLink} to={setup('configure')} startIcon={<ForumOutlined />}>Engage app assignment</Button>
      <Button component={RouterLink} to={setup('staging')} startIcon={<RocketLaunchOutlined />}>Test in customer staging</Button>
      <Button component={RouterLink} to={setup('production')} startIcon={<RocketLaunchOutlined />}>Review go-live</Button>
    </Stack>
    {releases.length ? <Box sx={{ mt: 2 }}>
      <Typography fontWeight={600} sx={{ mb: 1.5 }}>Daily operations</Typography>
      <TextField select fullWidth size="small" label="Working environment" value={selected?.id ?? ''}
        onChange={event => onSelectRelease(event.target.value)}>
        <MenuItem value="">Choose a target release</MenuItem>
        {releases.map(release => <MenuItem key={release.id} value={release.id}>{releaseEnvironment(release, offer)} / {release.id}</MenuItem>)}
      </TextField>
      <Stack direction="row" gap={0.5} flexWrap="wrap" sx={{ mt: 1 }}>
        {kinds.has('EXTERNAL_SERVICE') ? <Button component={RouterLink} to={scoped ? `/integrations?${scoped}` : '#'} disabled={!scoped}>Synchronization</Button> : null}
        {kinds.has('CUSTOMER_S3') ? <Button component={RouterLink} to={scoped ? `/document-knowledge?${scoped}` : '#'} disabled={!scoped}>Policies & documents</Button> : null}
        {kinds.has('INBOX_OPERATOR') ? <Button component={RouterLink} to={scoped ? `/human-support?${scoped}` : '#'} disabled={!scoped}>Human support</Button> : null}
      </Stack>
    </Box> : null}
  </Box>
}
