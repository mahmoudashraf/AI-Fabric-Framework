import { Alert, Paper, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import type { ReactNode } from 'react'
import { useParams } from 'react-router-dom'
import { getClientImplementation } from '../api/implementations'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { PageHeader } from '../components/PageHeader'
import { StatusChip } from '../components/StatusChip'
import { TierBadge } from '../components/TierBadge'
import { formatDateTime, titleize } from '../utils/format'

export function ImplementationRequestDetailPage() {
  const { requestId = '' } = useParams()
  const { api } = useSupabaseAuth()
  const implementationQuery = useQuery({
    queryKey: ['implementation', requestId],
    queryFn: () => getClientImplementation(api, requestId),
    enabled: Boolean(requestId),
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
              The request is waiting inside Shopify Companion admin for {implementation.shopDomain}. The merchant can approve or deny partner access from the Partners tab.
            </Typography>
            <Alert severity={implementation.status === 'APPROVED' ? 'success' : implementation.status === 'DENIED' ? 'warning' : 'info'} sx={{ mt: 2 }}>
              Status: {titleize(implementation.status)}
            </Alert>
            {implementation.approvalExpiresAt ? (
              <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 1 }}>
                Merchant review expires {formatDateTime(implementation.approvalExpiresAt)}
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
