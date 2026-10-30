import { useState } from 'react'
import { Link } from 'react-router-dom'
import { Alert, Box, Button, Chip, CircularProgress, Divider, IconButton, Stack, Table, TableBody, TableCell,
  TableContainer, TableHead, TableRow, Tooltip, Typography } from '@mui/material'
import ArrowBackRounded from '@mui/icons-material/ArrowBackRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { isCustomerSession } from '../auth/customerSession'
import { SupabaseIdentityForm } from '../auth/SupabaseIdentityForm'
import { CustomerConsoleTheme } from '../theme/CustomerConsoleTheme'
import { useSupportStaffAccess } from '../components/customer/useSupportStaffAccess'
import { PlatformApiError } from '../api/platformApi'

export function SupportTeamIntakePage() {
  const auth = usePlatformAuth()
  return <CustomerConsoleTheme><Box sx={{ minHeight: '100vh', bgcolor: 'background.default', color: 'text.primary', px: { xs: 2, md: 4 }, py: 3 }}>
    <Box sx={{ maxWidth: 1320, mx: 'auto' }}>
      <Stack direction={{ xs: 'column', sm: 'row' }} justifyContent="space-between" alignItems={{ xs: 'flex-start', sm: 'center' }} spacing={2} sx={{ mb: 3 }}>
        <Typography fontWeight={700}>LoomAI support</Typography>
        <Stack direction="row" spacing={1}>
          {isCustomerSession(auth.session) && <Button component={Link} to="/support" startIcon={<ArrowBackRounded />}>My requests</Button>}
          <Button component={Link} to="/">LoomAI products</Button>
          {auth.supabaseSession && <Button onClick={() => { void auth.signOut() }}>Sign out</Button>}
        </Stack>
      </Stack>
      {auth.customerAuthLoading ? <CircularProgress size={24} /> : !auth.customerConfig?.enabled || !auth.supabase
        ? <Alert severity="warning">Team sign-in is not configured.</Alert>
        : !auth.supabaseSession ? <Stack spacing={2.5} sx={{ maxWidth: 420, mx: 'auto', py: 5 }}>
          <Typography variant="h5">Sign in for team intake</Typography>
          <Typography color="text.secondary">Use your registered LoomAI Desk agent account. Support access is checked separately.</Typography>
          <SupabaseIdentityForm linking />
        </Stack> : <TeamIntake key={auth.supabaseSession.user.id} />}
    </Box>
  </Box></CustomerConsoleTheme>
}

function TeamIntake() {
  const [page, setPage] = useState(0)
  const query = useSupportStaffAccess(true, page)
  const denied = query.error instanceof PlatformApiError && [401, 403].includes(query.error.status)
  return <Stack spacing={2.5}>
    <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={2}>
      <Box><Typography variant="overline" color="primary.main">Integration team</Typography><Typography variant="h4">Team intake</Typography>
        <Typography color="text.secondary" sx={{ mt: 0.75 }}>Saved requests awaiting a completed first response. Conversations and replies stay in LoomAI Desk.</Typography></Box>
      <Tooltip title="Refresh intake"><IconButton aria-label="Refresh team intake" disabled={query.isFetching} onClick={() => { void query.refetch() }}><RefreshRounded /></IconButton></Tooltip>
    </Stack>
    <Divider />
    {query.isPending || query.isFetching ? <CircularProgress size={24} /> : query.isError
      ? <Alert severity={denied ? 'info' : 'warning'}>{denied
        ? 'Team intake is not available to this account. A current SUPPORT assignment is required.'
        : 'Team intake is temporarily unavailable. No case data is shown.'}</Alert>
      : query.data?.length === 0 ? <Typography color="text.secondary" sx={{ py: 4 }}>No requests are awaiting an initial response.</Typography>
      : <TableContainer><Table size="small" aria-label="Saved integration support intake">
        <TableHead><TableRow><TableCell>Request</TableCell><TableCell>Account & resources</TableCell><TableCell>Delivery</TableCell><TableCell>Status</TableCell><TableCell>Updated</TableCell></TableRow></TableHead>
        <TableBody>{query.data?.map(item => <TableRow key={item.caseId}>
          <TableCell sx={{ minWidth: 240, maxWidth: 420, verticalAlign: 'top' }}><Typography variant="caption" color="text.secondary">{item.reference}</Typography>
            <Typography variant="body2" fontWeight={600} sx={{ overflowWrap: 'anywhere', mt: 0.5 }}>{item.summary}</Typography>
            {item.details && <Typography variant="body2" color="text.secondary" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', mt: 0.75 }}>{item.details}</Typography>}</TableCell>
          <TableCell sx={{ minWidth: 200, maxWidth: 300, verticalAlign: 'top', overflowWrap: 'anywhere' }}>
            <Typography variant="body2">{item.accountLabel}</Typography>
            {item.resourceLabels.map((label, index) => <Typography key={index} variant="caption" color="text.secondary" display="block">{label}</Typography>)}
            <Typography variant="caption" color="text.secondary" display="block" sx={{ mt: 1 }}>{item.consent.summary}</Typography>
          </TableCell>
          <TableCell sx={{ verticalAlign: 'top' }}><Chip size="small" variant="outlined" label={item.initialMessageDelivery.split('_').join(' ')} /></TableCell>
          <TableCell sx={{ verticalAlign: 'top' }}><Chip size="small" variant="outlined" label={item.status} /></TableCell>
          <TableCell sx={{ whiteSpace: 'nowrap', verticalAlign: 'top' }}><Typography variant="caption">{new Date(item.updatedAt).toLocaleString()}</Typography></TableCell>
        </TableRow>)}</TableBody>
      </Table></TableContainer>}
    {!query.isError && <Stack direction="row" justifyContent="flex-end" spacing={1}>
      <Button disabled={page === 0 || query.isFetching} onClick={() => setPage(value => value - 1)}>Previous</Button>
      <Button disabled={(query.data?.length || 0) < 25 || query.isFetching} onClick={() => setPage(value => value + 1)}>Next</Button>
    </Stack>}
  </Stack>
}
