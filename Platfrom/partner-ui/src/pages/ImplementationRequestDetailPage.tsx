import { Alert, Button, Paper, Stack, TextField, Typography } from '@mui/material'
import ContentCopyOutlinedIcon from '@mui/icons-material/ContentCopyOutlined'
import LinkOutlinedIcon from '@mui/icons-material/LinkOutlined'
import { useMutation, useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useState } from 'react'
import { useParams } from 'react-router-dom'
import { createStoreAccessLink, getClientImplementation } from '../api/implementations'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { TierBadge } from '../components/TierBadge'
import { formatDateTime, titleize } from '../utils/format'

export function ImplementationRequestDetailPage() {
  const { requestId = '' } = useParams()
  const { api } = useSupabaseAuth()
  const [copied, setCopied] = useState(false)
  const implementationQuery = useQuery({
    queryKey: ['implementation', requestId],
    queryFn: () => getClientImplementation(api, requestId),
    enabled: Boolean(requestId),
  })
  const linkMutation = useMutation({
    mutationFn: () => createStoreAccessLink(api, requestId),
    onSuccess: async (link) => {
      await navigator.clipboard?.writeText(link.approvalUrl)
      setCopied(true)
      await implementationQuery.refetch()
    },
  })

  if (implementationQuery.isError) {
    return <Alert severity="error">Implementation request is not available to this partner workspace.</Alert>
  }
  const implementation = implementationQuery.data
  return (
    <>
      <PageHeader
        title={implementation?.clientName ?? 'Implementation request'}
        subtitle={implementation?.shopDomain}
        breadcrumbs={[{ label: 'Dashboard', to: '/' }, { label: 'Implementation request' }]}
      />
      {!implementation ? null : (
        <Stack spacing={2}>
          <Paper sx={{ p: 2 }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
              <Stack spacing={1}>
                <StatusChip status={implementation.status} />
                <Typography variant="h2">{implementation.clientName}</Typography>
                <Typography color="text.secondary">{implementation.contactEmail ?? 'No contact email provided'}</Typography>
              </Stack>
              <Stack direction="row" spacing={3} flexWrap="wrap">
                <Metric label="Tier" value={<TierBadge tier={implementation.requestedTier} />} />
                <Metric label="Vertical" value={implementation.vertical ? titleize(implementation.vertical) : 'Not set'} />
                <Metric label="Created" value={formatDateTime(implementation.createdAt)} />
              </Stack>
            </Stack>
          </Paper>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h3">Merchant approval</Typography>
            <Typography color="text.secondary" sx={{ mt: 0.75 }}>
              Share this link with the merchant. Approval grants scoped implementation support visibility only.
            </Typography>
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ mt: 2 }}>
              <TextField value={implementation.approvalUrl ?? linkMutation.data?.approvalUrl ?? ''} fullWidth InputProps={{ readOnly: true }} placeholder="Create approval link to populate this field" />
              <Button
                variant="contained"
                startIcon={<LinkOutlinedIcon />}
                onClick={() => linkMutation.mutate()}
                disabled={linkMutation.isPending}
              >
                Create link
              </Button>
              <Button
                variant="outlined"
                startIcon={<ContentCopyOutlinedIcon />}
                disabled={!(implementation.approvalUrl ?? linkMutation.data?.approvalUrl)}
                onClick={async () => {
                  await navigator.clipboard?.writeText(implementation.approvalUrl ?? linkMutation.data?.approvalUrl ?? '')
                  setCopied(true)
                }}
              >
                Copy
              </Button>
            </Stack>
            {copied ? <Alert severity="success" sx={{ mt: 2 }}>Approval link copied.</Alert> : null}
            {implementation.approvalExpiresAt ? (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                Expires {formatDateTime(implementation.approvalExpiresAt)}
              </Typography>
            ) : null}
          </Paper>
          <Paper sx={{ p: 2 }}>
            <Typography variant="h3">Requested surfaces</Typography>
            <Stack direction="row" spacing={1} flexWrap="wrap" sx={{ mt: 1.5 }}>
              {implementation.requestedSurfaces.map((surface) => (
                <Typography key={surface} sx={{ px: 1, py: 0.5, border: 1, borderColor: 'divider', borderRadius: 2 }}>
                  {titleize(surface)}
                </Typography>
              ))}
            </Stack>
          </Paper>
        </Stack>
      )}
    </>
  )
}

function Metric({ label, value }: { label: string; value: ReactNode }) {
  return (
    <Stack spacing={0.5}>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography component="div" fontWeight={700}>{value}</Typography>
    </Stack>
  )
}
