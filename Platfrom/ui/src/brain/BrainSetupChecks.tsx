import { useEffect, useId, useState } from 'react'
import CheckCircleOutlineRounded from '@mui/icons-material/CheckCircleOutlineRounded'
import ErrorOutlineRounded from '@mui/icons-material/ErrorOutlineRounded'
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded'
import CloseRounded from '@mui/icons-material/CloseRounded'
import HelpOutlineRounded from '@mui/icons-material/HelpOutlineRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { Alert, Box, Button, Drawer, IconButton, LinearProgress, Stack, Tooltip, Typography } from '@mui/material'
import { setupChecksMatchDraft, type BrainSetupChecksState } from './setupChecks'

const time = (value: string | null) => value ? new Date(value).toLocaleString() : 'Not checked'

export function BrainSetupChecks({ checks, canCheck = false, draftId, revisionNumber }: {
  checks: BrainSetupChecksState; canCheck?: boolean; draftId?: string; revisionNumber?: number
}) {
  const [expanded, setExpanded] = useState(false)
  const [now, setNow] = useState(Date.now)
  const detailsId = useId()
  const result = checks.data
  const draftKnown = draftId !== undefined && revisionNumber !== undefined
  const expiresAt = result?.services.reduce((expiry, service) => {
    const checked = service.checkedAt && Date.parse(service.checkedAt)
    return service.status !== 'NOT_TESTED' && checked ? Math.min(expiry, checked + 5 * 60_000) : expiry
  }, Infinity) ?? Infinity
  const expired = expiresAt <= now
  useEffect(() => {
    setNow(Date.now())
    if (!Number.isFinite(expiresAt) || expiresAt <= Date.now()) return
    const timer = window.setTimeout(() => setNow(Date.now()), expiresAt - Date.now() + 1)
    return () => window.clearTimeout(timer)
  }, [expiresAt])
  const current = setupChecksMatchDraft(result, draftId, revisionNumber) && !expired
  const Icon = !current || !result || result.notTestedCount > 0 && !result.failedCount ? HelpOutlineRounded
    : result.failedCount ? ErrorOutlineRounded : CheckCircleOutlineRounded
  return <Box component="section" aria-label="Setup connections" sx={{ minWidth: 0 }}>
    <Stack direction={{ xs: 'column', sm: 'row' }} gap={1} alignItems={{ sm: 'center' }}>
      <Stack direction="row" gap={1} alignItems="center" sx={{ flex: 1, minWidth: 0 }}>
        <Icon fontSize="small" color={!current || !result ? 'action' : result.failedCount ? 'warning' : result.notTestedCount ? 'action' : 'success'} />
        <Typography variant="body2" role="status" sx={{ overflowWrap: 'anywhere' }}>
          {checks.pending ? 'Checking saved connections...'
            : result ? `${current ? 'Connections' : draftKnown || expired ? 'Previous check' : 'Recorded connections'}: ${result.verifiedCount} verified · ${result.failedCount} failed · ${result.notTestedCount} not tested`
              : checks.isFetching ? 'Loading connection status...' : checks.isError ? 'Connection status unavailable' : 'Connections not checked yet'}
        </Typography>
      </Stack>
      {result && <Button size="small" onClick={() => setExpanded(true)} aria-expanded={expanded} aria-controls={detailsId}
        endIcon={<ChevronRightRounded />}>
        Details
      </Button>}
      {!expanded && canCheck && Boolean(result?.failedCount) && <Button size="small" startIcon={<RefreshRounded />} disabled={checks.pending}
        onClick={() => { void checks.run('FAILED').catch(() => undefined) }}>Retry failed checks</Button>}
    </Stack>
    {checks.pending && <LinearProgress aria-label="Checking connections" sx={{ mt: 1 }} />}
    {checks.runError && <Alert severity="warning" sx={{ mt: 1 }}>{checks.afterSave ? 'Configuration saved. ' : ''}{checks.runError}</Alert>}
    {checks.isError && !checks.runError && <Alert severity="warning" sx={{ mt: 1 }}>Recorded connection checks could not be loaded. Saving and publishing status are separate.</Alert>}
    {result && !current && <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: .5 }}>{expired ? 'These connection results have expired. Check setup again.'
      : draftKnown ? 'These results are from another draft revision. Check setup again for the saved changes.' : 'Current draft details are unavailable. Recorded checks are shown.'}</Typography>}
    <Drawer anchor="right" open={expanded} onClose={() => setExpanded(false)} slotProps={{ paper: {
      role: 'dialog', 'aria-label': 'Connection checks', id: detailsId,
      sx: { width: { xs: '100%', sm: 480 }, maxWidth: '100%', display: 'flex', flexDirection: 'column' },
    } }}>
      <Stack direction="row" alignItems="center" gap={1} sx={{ px: 2.5, py: 2, borderBottom: 1, borderColor: 'divider' }}>
        <Typography component="h2" fontSize={19} fontWeight={600} sx={{ flex: 1 }}>Connection checks</Typography>
        <Tooltip title="Close connection details"><IconButton aria-label="Close connection details" onClick={() => setExpanded(false)}><CloseRounded /></IconButton></Tooltip>
      </Stack>
      <Box sx={{ px: 2.5, flex: 1, minHeight: 0, overflowY: 'auto' }}>
      {result?.services.length === 0 && <Typography variant="body2" color="text.secondary" sx={{ py: 1 }}>No external services are configured for this draft.</Typography>}
      {result?.services.map(service => <Box key={service.id} sx={{ py: 1.25, borderBottom: 1, borderColor: 'divider', overflowWrap: 'anywhere' }}>
        <Stack direction="row" justifyContent="space-between" gap={1}><Typography variant="body2" fontWeight={600}>{service.label}</Typography>
          <Typography variant="caption" color={service.status === 'FAILED' ? 'warning.main' : service.status === 'VERIFIED' ? 'success.main' : 'text.secondary'} sx={{ flexShrink: 0 }}>
            {service.status === 'VERIFIED' ? 'Verified' : service.status === 'FAILED' ? 'Failed' : 'Not tested'}
          </Typography></Stack>
        <Typography variant="body2" color="text.secondary">{service.message}</Typography>
        {service.usedBy.length > 0 && <Typography variant="caption" display="block" color="text.secondary">Used by: {service.usedBy.join(', ')}</Typography>}
        <Typography variant="caption" display="block" color="text.secondary">{time(service.checkedAt)}{service.httpStatus !== null ? ` · HTTP ${service.httpStatus}` : ''}</Typography>
      </Box>)}
      {result?.checkedAt && <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>Last setup check: {time(result.checkedAt)}</Typography>}
      </Box>
      <Stack gap={1} sx={{ px: 2.5, py: 2, borderTop: 1, borderColor: 'divider' }}>
        {canCheck && Boolean(result?.failedCount) && <Button size="small" startIcon={<RefreshRounded />} disabled={checks.pending}
          sx={{ alignSelf: 'flex-start' }} onClick={() => { void checks.run('FAILED').catch(() => undefined) }}>Retry failed checks</Button>}
        {checks.pending && <LinearProgress aria-label="Checking connections" />}
        {checks.runError && <Alert severity="warning">{checks.runError}</Alert>}
        <Typography variant="caption" color="text.secondary">Checks use a supported provider probe or a configured safe GET endpoint. They do not run AI inference or business actions, or verify permission for every action.</Typography>
      </Stack>
    </Drawer>
  </Box>
}
