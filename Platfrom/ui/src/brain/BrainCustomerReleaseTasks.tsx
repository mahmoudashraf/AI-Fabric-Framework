import { Alert, Box, Button, Stack, Typography } from '@mui/material'
import ArrowForwardRounded from '@mui/icons-material/ArrowForwardRounded'
import { Link } from 'react-router-dom'
import type { PlatformAuthSessionSummary } from '../api/platformApi'
import { CustomerDeploymentConsole, type CustomerReleaseWorkspace } from '../components/customer/CustomerDeploymentConsole'
import { useBrainWorkspace } from './BrainWorkspaceContext'

export function BrainCustomerReleaseTasks({ session, section, canEdit, canOperate }: {
  session: PlatformAuthSessionSummary
  section: CustomerReleaseWorkspace['section']
  canEdit: boolean
  canOperate: boolean
}) {
  const { brainId, targetProfileId, href } = useBrainWorkspace()
  if (!canEdit || !canOperate) return <Alert severity="info">Managing connections and app assignments requires editor and operator access. Your current access is read-only for this task.</Alert>
  return <Stack spacing={2}>
    <Box><Typography component="h2" sx={{ fontSize: 20, fontWeight: 650 }}>
      {section === 'promotion' ? 'Promote your tested AI to production' : section === 'assignment' ? 'Connect the verified staging experience' : 'Prepare your staging connections'}
    </Typography><Typography variant="body2" color="text.secondary" sx={{ mt: 0.5 }}>
      {section === 'promotion' ? 'Review the staging candidate, deploy it, then explicitly switch the selected app after production checks pass.'
        : section === 'assignment' ? 'Choose the app assignment to use for staging. Your live production app is not switched automatically.'
          : 'Connect the services required by your starting template. These are the same saved connections used by publishing and verification.'}
    </Typography></Box>
    <CustomerDeploymentConsole session={session} releaseWorkspace={{ brainId, targetProfileId, section }} />
    {section === 'assignment' && <Button component={Link} to={href('try')} endIcon={<ArrowForwardRounded />} sx={{ alignSelf: 'flex-start' }}>Try your AI</Button>}
  </Stack>
}
