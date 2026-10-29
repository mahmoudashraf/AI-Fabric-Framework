import { useState } from 'react'
import { Alert, Box, Button, Chip, IconButton, Paper, Stack, Tooltip, Typography } from '@mui/material'
import ContentCopyRounded from '@mui/icons-material/ContentCopyRounded'
import type { AIWorkspaceInstallationSummary } from '../../api/platformApi'

export function EngageDomainInstructions({ domain, pending, verify }: {
  domain: AIWorkspaceInstallationSummary['domains'][number]; pending: boolean; verify: () => void
}) {
  const [copyState, setCopyState] = useState<string | null>(null)
  const custom = domain.type === 'CUSTOMER_CUSTOM'
  const platformReady = Boolean(domain.routingRecordValue)
  const verified = domain.status === 'VERIFIED'
  const copy = async (label: string, value: string) => {
    try { await navigator.clipboard.writeText(value); setCopyState(`${label} copied.`) }
    catch { setCopyState('Clipboard unavailable. Select the record below to copy it.') }
  }
  const record = (type: string, name: string, value: string) => <Box sx={{ py: 1, minWidth: 0 }}>
    <Typography variant="caption" color="text.secondary">{type} record</Typography>
    {[[`${type} name`, name], [`${type} value`, value]].map(([label, content]) => <Stack key={label} direction="row" alignItems="center" gap={1}>
      <Box sx={{ minWidth: 0, flex: 1 }}><Typography variant="caption" color="text.secondary">{label.endsWith('name') ? 'Name' : 'Value'}</Typography><Typography variant="body2" sx={{ fontFamily: 'monospace', overflowWrap: 'anywhere', userSelect: 'all' }}>{content}</Typography></Box>
      <Tooltip title={`Copy ${label}`}><IconButton size="small" aria-label={`Copy ${label}`} onClick={() => void copy(label, content)}><ContentCopyRounded fontSize="small" /></IconButton></Tooltip>
    </Stack>)}
  </Box>
  return <Paper variant="outlined" sx={{ p: 1.5, borderRadius: 1 }}>
    <Stack direction="row" justifyContent="space-between" alignItems="center" spacing={1}>
      <Box sx={{ minWidth: 0 }}><Typography variant="body2" fontWeight={700} sx={{ overflowWrap: 'anywhere' }}>{domain.hostname}</Typography><Typography variant="caption" color="text.secondary">{custom ? 'Your domain' : 'Platform address'}</Typography></Box>
      <Chip size="small" label={verified ? 'Verified' : domain.status === 'ROUTING_PENDING' ? 'Route / HTTPS pending' : 'DNS verification pending'} color={verified ? 'success' : 'warning'} />
    </Stack>
    {custom && !verified ? <Stack spacing={1} sx={{ mt: 1 }}>
      {!platformReady ? <Alert severity="warning">Platform setup is incomplete: the shared Engage CNAME target is not configured. Contact LoomAI support; there is no routing value to add yet.</Alert> : <Typography variant="body2" color="text.secondary">Add both records with your domain's DNS provider. Keep the full hostname or use the equivalent relative name shown by that provider.</Typography>}
      {domain.dnsRecordName && domain.dnsRecordValue ? record('TXT', domain.dnsRecordName, domain.dnsRecordValue) : <Alert severity="warning">Ownership instructions are not available. Refresh the app before changing DNS.</Alert>}
      {platformReady ? record('CNAME', domain.routingRecordName ?? domain.hostname, domain.routingRecordValue!) : null}
      <Typography variant="caption" color="text.secondary">Verification checks ownership, routing and HTTPS. A queued route is not yet live. If you use magic links or OAuth, add this exact HTTPS address to your identity provider's allowed redirects.</Typography>
    </Stack> : null}
    {domain.lastVerificationError ? <Alert severity="warning" sx={{ mt: 1 }}>{domain.lastVerificationError}</Alert> : null}
    {!verified ? <Button size="small" sx={{ mt: 1 }} disabled={pending || (custom && !platformReady)} onClick={verify}>{pending ? 'Checking...' : custom ? 'Verify domain' : 'Verify route'}</Button> : null}
    {copyState ? <Typography role="status" variant="caption" display="block" sx={{ mt: 1 }}>{copyState}</Typography> : null}
  </Paper>
}
