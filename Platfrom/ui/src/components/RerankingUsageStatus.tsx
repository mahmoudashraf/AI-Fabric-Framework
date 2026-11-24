import { Alert, Box, CircularProgress, Stack, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { usePlatformAuth } from '../auth/PlatformAuthProvider'
import { platformRequest } from '../api/platformApi'

type Status = { deploymentId: string; targetProfileId: string; releaseId: string; status: string;
  limits: { costNanoUsd: number; totalTokens: number } | null;
  accountedUsage: { costNanoUsd: number; inputTokens: number; outputTokens: number } | null;
  expiresAt: string | null; providerAttempts: number | null }

export function RerankingUsageStatus({ brainId, targetProfileId }: { brainId: string; targetProfileId?: string }) {
  const { session } = usePlatformAuth()
  const query = useQuery({ queryKey: ['reranking-usage', session?.actorId, session?.customerId, brainId, targetProfileId],
    enabled: Boolean(targetProfileId), retry: false, staleTime: 10000,
    queryFn: async ({ signal }) => {
      const value = await platformRequest<Status>(`/api/deployments/${encodeURIComponent(brainId)}/reranking-usage?${new URLSearchParams({ targetProfileId: targetProfileId! })}`, { signal })
      if (value.deploymentId !== brainId || value.targetProfileId !== targetProfileId) throw new Error('Usage does not match the selected environment.')
      return value
    } })
  const money = (nano: number) => new Intl.NumberFormat('en-US', { style: 'currency', currency: 'USD', minimumFractionDigits: 2, maximumFractionDigits: 6 }).format(nano / 1e9)
  if (!targetProfileId) return <Typography variant="body2" color="text.secondary">No environment is selected for allowance readback.</Typography>
  if (query.isPending) return <CircularProgress size={18} aria-label="Reading reranking allowance" />
  if (query.isError) return <Alert severity="info">Reranking allowance is unavailable: {query.error.message}</Alert>
  const value = query.data
  if (!value) return null
  return <Box component="section" aria-label="Applied reranking allowance">
    <Stack gap={0.75}>
      <Typography variant="body2" fontWeight={600}>Applied environment: {value.status === 'OFF' ? 'Reranking off' : value.status.split('_').join(' ').toLowerCase()}</Typography>
      {value.limits && value.accountedUsage && <Typography variant="body2" color="text.secondary">
        Accounted {money(value.accountedUsage.costNanoUsd)} of {money(value.limits.costNanoUsd)} · {value.providerAttempts == null ? 'Attempt count unavailable' : `${value.providerAttempts} provider attempts`}
      </Typography>}
      {value.status !== 'OFF' && value.status !== 'ACTIVE' && <Alert severity="warning">An active, approved reranking allowance is required before requests can run.</Alert>}
      {value.expiresAt && <Typography variant="caption" color="text.secondary">Allowance expires {new Date(value.expiresAt).toLocaleString()}. Accounted usage includes held unknown outcomes, not an invoice.</Typography>}
    </Stack>
  </Box>
}
