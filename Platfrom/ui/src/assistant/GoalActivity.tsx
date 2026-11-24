import { useEffect, useRef, useState } from 'react'
import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Alert, Box, Chip, IconButton, LinearProgress, Stack, Tooltip, Typography } from '@mui/material'
import ChevronLeftRounded from '@mui/icons-material/ChevronLeftRounded'
import ChevronRightRounded from '@mui/icons-material/ChevronRightRounded'
import RefreshOutlined from '@mui/icons-material/RefreshOutlined'
import { stringifyExactJson } from '../lib/losslessJson'
import { assistantApi } from './api'
import { operationPageSize, readOperationPage, uniqueOperations, type OperationPage } from './operationPage'
import { ProviderDiagnosticsDetails } from './ProviderDiagnosticsDetails'
import type { AssistantGoal, AssistantOperation } from './types'

export function GoalActivity({ sessionId, goal, initialOperations }: { sessionId: string; goal: AssistantGoal; initialOperations: AssistantOperation[] }) {
  const [open, setOpen] = useState(false)
  return <Box component="details" onToggle={event => { if (event.target === event.currentTarget) setOpen(event.currentTarget.open) }} sx={{ mt: .75, fontSize: 12 }}>
    <summary>Activity &middot; Segment {goal.segment}</summary>
    {goal.progress?.workItems?.map(item => <Stack key={item.id} direction="row" gap={.5} flexWrap="wrap" sx={{ py: .5 }}>
      <Typography variant="caption" sx={{ flex: '1 1 140px' }}>{item.label}{item.message ? ` \u00b7 ${item.message}` : ''}</Typography>
      <Chip size="small" variant="outlined" label={item.state} sx={{ maxWidth: '100%' }} />
    </Stack>)}
    <GoalReceipts sessionId={sessionId} goal={goal} initialOperations={initialOperations} open={open} />
    <ProviderDiagnosticsDetails value={goal.providerDiagnostics} />
    {goal.usage && <Typography variant="caption" display="block">Cumulative: {goal.usage.modelCalls ?? 'Unknown'} model calls &middot; {goal.usage.corrections ?? 'Unknown'} corrections</Typography>}
  </Box>
}

function GoalReceipts({ sessionId, goal, initialOperations, open }: { sessionId: string; goal: AssistantGoal; initialOperations: AssistantOperation[]; open: boolean }) {
  const [offset, setOffset] = useState(0), [lastPage, setLastPage] = useState<OperationPage | null>(null)
  const refreshedCount = useRef(0)
  const initial = uniqueOperations(initialOperations)
  const total = Math.max(goal.operationCount ?? 0, initial.length)
  const paginated = total > operationPageSize
  const query = useQuery({
    queryKey: ['configuration-assistant-goal-operations', sessionId, goal.id, goal.cancellationEpoch ?? 0, offset],
    queryFn: async ({ signal }) => readOperationPage(await assistantApi.goalOperations(sessionId, goal.id, offset, operationPageSize, signal), goal.id, offset, operationPageSize),
    enabled: open && paginated, retry: false, staleTime: Infinity, gcTime: 0, refetchOnWindowFocus: false, placeholderData: keepPreviousData,
  })
  useEffect(() => { if (query.data && !query.isPlaceholderData) setLastPage(query.data) }, [query.data, query.isPlaceholderData])
  useEffect(() => {
    if (!open || !paginated || !query.data || query.isFetching || query.isError || query.isPlaceholderData || total <= query.data.total || total <= refreshedCount.current) return
    refreshedCount.current = total
    void query.refetch()
  }, [open, paginated, total, query.data, query.isFetching, query.isError, query.isPlaceholderData, query.refetch])
  const page = query.data ?? lastPage
  const operations = page?.operations ?? initial.slice(0, operationPageSize)
  const pageOffset = page?.offset ?? 0, count = page?.recordCount ?? operations.length, knownTotal = Math.max(total, page?.total ?? 0)
  const nextOffset = page?.nextOffset
  if (!knownTotal) return null
  return <Box component="section" aria-label="Goal operation receipts" sx={{ mt: .75, minWidth: 0 }}>
    {paginated && <Stack direction="row" alignItems="center" gap={.25} sx={{ minHeight: 32 }}>
      <Typography variant="caption" sx={{ flex: 1, minWidth: 0 }}>Receipts {count ? pageOffset + 1 : 0}-{pageOffset + count} of {knownTotal}</Typography>
      <Tooltip title="Previous receipts"><span><IconButton size="small" aria-label="Previous receipts" disabled={query.isFetching || offset === 0} onClick={() => setOffset(Math.max(0, offset - operationPageSize))}><ChevronLeftRounded fontSize="small" /></IconButton></span></Tooltip>
      <Tooltip title="Next receipts"><span><IconButton size="small" aria-label="Next receipts" disabled={query.isFetching || query.isError || !page?.hasMore || nextOffset === undefined} onClick={() => { if (nextOffset !== undefined) setOffset(nextOffset) }}><ChevronRightRounded fontSize="small" /></IconButton></span></Tooltip>
      <Tooltip title={query.isError ? 'Retry receipts' : 'Refresh receipts'}><span><IconButton size="small" aria-label={query.isError ? 'Retry receipts' : 'Refresh receipts'} disabled={query.isFetching} onClick={() => void query.refetch()}><RefreshOutlined fontSize="small" /></IconButton></span></Tooltip>
    </Stack>}
    {query.isFetching && <LinearProgress aria-label="Loading operation receipts" sx={{ mb: .5 }} />}
    {query.isError && <Alert severity="error" sx={{ py: 0, mb: .5, maxHeight: 90, overflowY: 'auto' }}>{query.error instanceof Error ? query.error.message : 'The receipt page could not be loaded.'}</Alert>}
    <Box data-goal-receipts sx={{ height: paginated ? 152 : undefined, maxHeight: 152, overflowY: 'auto', minWidth: 0 }}>
      {operations.map(operation => <Box component="details" data-operation-id={operation.operationId} key={operation.operationId} sx={{ my: .75 }}>
        <summary>{operation.operationId} &middot; {operation.action === 'authoring_undo_request' ? 'Undone' : 'Saved to draft'}</summary>
        {operation.protectedBodyErased ? <Typography variant="caption" display="block">Receipt details removed.</Typography> : <Box component="pre" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', m: 0, fontSize: 11 }}>{stringifyExactJson({ receipt: operation.receipt, diff: operation.diff, findings: operation.findings }, 2)}</Box>}
      </Box>)}
    </Box>
  </Box>
}
