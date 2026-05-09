import { Alert, Box, Button, Chip, Divider, LinearProgress, Paper, Stack, TextField, Typography } from '@mui/material'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useParams } from 'react-router-dom'
import { useState } from 'react'
import {
  approveMerchantAccess,
  denyMerchantAccess,
  getMerchantWorkspace,
  requestMerchantProductionPromotion,
  requestMerchantRollback,
  revokeMerchantAccess,
} from '../api/merchant'
import { useSupabaseAuth } from '../auth/SupabaseProvider'
import { StatusChip } from '../components/StatusChip'
import { formatDateTime } from '../utils/format'

export function MerchantApprovalPage() {
  const { approvalCode = '' } = useParams()
  const { api } = useSupabaseAuth()
  const queryClient = useQueryClient()
  const [approverName, setApproverName] = useState('')
  const [approverEmail, setApproverEmail] = useState('')
  const [decisionReason, setDecisionReason] = useState('')
  const [rollbackReason, setRollbackReason] = useState('')

  const workspaceQuery = useQuery({
    queryKey: ['merchant-workspace', approvalCode],
    queryFn: () => getMerchantWorkspace(api, approvalCode),
    enabled: approvalCode.length > 0,
  })

  const refreshWorkspace = async () => {
    await queryClient.invalidateQueries({ queryKey: ['merchant-workspace', approvalCode] })
  }

  const approveMutation = useMutation({
    mutationFn: () => approveMerchantAccess(api, approvalCode, { approverName, approverEmail: approverEmail || undefined, approvedScope: 'FULL_STORE_ACCESS' }),
    onSuccess: refreshWorkspace,
  })
  const denyMutation = useMutation({
    mutationFn: () => denyMerchantAccess(api, approvalCode, { approverName, approverEmail: approverEmail || undefined, approvedScope: 'FULL_STORE_ACCESS', decisionReason }),
    onSuccess: refreshWorkspace,
  })
  const revokeMutation = useMutation({
    mutationFn: () => revokeMerchantAccess(api, approvalCode, { approverName, approverEmail: approverEmail || undefined, approvedScope: 'FULL_STORE_ACCESS', decisionReason }),
    onSuccess: refreshWorkspace,
  })
  const promoteMutation = useMutation({
    mutationFn: () => requestMerchantProductionPromotion(api, approvalCode),
    onSuccess: refreshWorkspace,
  })
  const rollbackMutation = useMutation({
    mutationFn: () => requestMerchantRollback(api, approvalCode, { requesterName: approverName, requesterEmail: approverEmail || undefined, reason: rollbackReason }),
    onSuccess: refreshWorkspace,
  })

  if (workspaceQuery.isLoading) {
    return <LinearProgress />
  }
  if (workspaceQuery.isError || !workspaceQuery.data) {
    return (
      <Box sx={{ minHeight: '100vh', display: 'grid', placeItems: 'center', p: 2, bgcolor: 'background.default' }}>
        <Paper sx={{ width: '100%', maxWidth: 560, p: 3 }}>
          <Alert severity="error">{workspaceQuery.error instanceof Error ? workspaceQuery.error.message : 'Merchant launch workspace could not be loaded.'}</Alert>
        </Paper>
      </Box>
    )
  }

  const workspace = workspaceQuery.data
  const request = workspace.accessRequest
  const readiness = workspace.launchReadiness
  const canApprove = workspace.availableActions.includes('APPROVE_PARTNER_ACCESS')
  const canDeny = workspace.availableActions.includes('DENY_PARTNER_ACCESS')
  const canRevoke = workspace.availableActions.includes('REVOKE_PARTNER_ACCESS')
  const canPromote = workspace.availableActions.includes('REQUEST_PRODUCTION_PROMOTION')
  const canRollback = workspace.availableActions.includes('REQUEST_ROLLBACK')
  const hasApprover = approverName.trim().length >= 2

  return (
    <Box sx={{ minHeight: '100vh', bgcolor: 'background.default', p: { xs: 2, md: 4 } }}>
      <Stack spacing={2} sx={{ maxWidth: 960, mx: 'auto' }}>
        <Paper sx={{ p: 3 }}>
          <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
            <Stack spacing={0.75}>
              <Typography variant="h1">Loom Companion launch approval</Typography>
              <Typography color="text.secondary">{request.shopDomain}</Typography>
            </Stack>
            <StatusChip status={request.status} />
          </Stack>
          <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr', md: 'repeat(3, 1fr)' }, gap: 1.5, mt: 2 }}>
            <Info label="Partner" value={request.partnerName} />
            <Info label="Store" value={request.clientName} />
            <Info label="Expires" value={formatDateTime(request.expiresAt)} />
          </Box>
          {request.notes ? <Typography sx={{ mt: 2 }} color="text.secondary">{request.notes}</Typography> : null}
        </Paper>

        <Paper sx={{ p: 3 }}>
          <Typography variant="h3">Merchant decision</Typography>
          <Stack spacing={1.5} sx={{ mt: 2 }}>
            <TextField label="Approver name" value={approverName} onChange={(event) => setApproverName(event.target.value)} />
            <TextField label="Approver email" type="email" value={approverEmail} onChange={(event) => setApproverEmail(event.target.value)} />
            <TextField label="Decision note" value={decisionReason} onChange={(event) => setDecisionReason(event.target.value)} multiline minRows={2} />
            <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1}>
              {canApprove ? <Button variant="contained" disabled={!hasApprover || approveMutation.isPending} onClick={() => approveMutation.mutate()}>Approve access</Button> : null}
              {canDeny ? <Button color="warning" variant="outlined" disabled={!hasApprover || denyMutation.isPending} onClick={() => denyMutation.mutate()}>Deny access</Button> : null}
              {canRevoke ? <Button color="error" variant="outlined" disabled={!hasApprover || revokeMutation.isPending} onClick={() => revokeMutation.mutate()}>Revoke access</Button> : null}
            </Stack>
          </Stack>
          <MutationResult mutation={approveMutation} success="Partner access approved." />
          <MutationResult mutation={denyMutation} success="Partner access denied." />
          <MutationResult mutation={revokeMutation} success="Partner access revoked." />
        </Paper>

        {readiness ? (
          <Paper sx={{ p: 3 }}>
            <Stack direction={{ xs: 'column', md: 'row' }} spacing={2} justifyContent="space-between">
              <Stack spacing={0.75}>
                <Typography variant="h3">Launch readiness</Typography>
                <Typography color="text.secondary">{readiness.merchantAction}</Typography>
              </Stack>
              <StatusChip status={readiness.status} />
            </Stack>
            <Box sx={{ display: 'grid', gridTemplateColumns: { xs: '1fr 1fr', md: 'repeat(4, 1fr)' }, gap: 1.5, mt: 2 }}>
              <Info label="Staging" value={readiness.stagingReady ? 'Ready' : 'Needs setup'} />
              <Info label="Evidence" value={readiness.evidenceReady ? 'Ready' : 'Needed'} />
              <Info label="Go-live" value={readiness.goLiveEligible ? 'Eligible' : 'Blocked'} />
              <Info label="Checked" value={formatDateTime(readiness.checkedAt)} />
            </Box>
            {readiness.blockers.length ? (
              <Stack spacing={0.75} sx={{ mt: 2 }}>
                {readiness.blockers.map((blocker) => <Alert key={blocker} severity="warning">{blocker}</Alert>)}
              </Stack>
            ) : null}
            <Button sx={{ mt: 2 }} variant="contained" disabled={!canPromote || promoteMutation.isPending} onClick={() => promoteMutation.mutate()}>
              Go production
            </Button>
            <MutationResult mutation={promoteMutation} success="Production promotion requested." />
          </Paper>
        ) : null}

        <Paper sx={{ p: 3 }}>
          <Typography variant="h3">Evidence and support</Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap' }}>
            {workspace.evidenceBundles.map((bundle) => <Chip key={bundle.id} label={`${bundle.bundleKind}: ${bundle.status}`} />)}
            {workspace.evidenceBundles.length === 0 ? <Typography color="text.secondary">No merchant-safe evidence bundles are attached yet.</Typography> : null}
          </Stack>
          <Divider sx={{ my: 2 }} />
          <Stack spacing={1}>
            {workspace.supportEscalations.map((item) => (
              <Box key={item.id}>
                <Typography fontWeight={700}>{item.title}</Typography>
                <Typography color="text.secondary">{item.status} · {item.nextAction ?? 'No next action recorded'}</Typography>
              </Box>
            ))}
            {workspace.supportEscalations.length === 0 ? <Typography color="text.secondary">No visible support escalations are open.</Typography> : null}
          </Stack>
          {canRollback ? (
            <Stack spacing={1.5} sx={{ mt: 2 }}>
              <TextField label="Rollback or deactivation reason" value={rollbackReason} onChange={(event) => setRollbackReason(event.target.value)} multiline minRows={2} />
              <Button color="error" variant="outlined" disabled={!hasApprover || rollbackReason.trim().length < 4 || rollbackMutation.isPending} onClick={() => rollbackMutation.mutate()}>
                Request rollback/deactivation
              </Button>
            </Stack>
          ) : null}
          <MutationResult mutation={rollbackMutation} success="Rollback/deactivation request recorded." />
        </Paper>

        <Paper sx={{ p: 3 }}>
          <Typography variant="h3">Current limits</Typography>
          <Stack spacing={0.75} sx={{ mt: 1 }}>
            {workspace.limitations.map((item) => <Typography key={item} color="text.secondary">{item}</Typography>)}
          </Stack>
        </Paper>
      </Stack>
    </Box>
  )
}

function Info({ label, value }: { label: string; value: string }) {
  return (
    <Box>
      <Typography variant="caption" color="text.secondary">{label}</Typography>
      <Typography fontWeight={700}>{value}</Typography>
    </Box>
  )
}

function MutationResult({ mutation, success }: { mutation: { isSuccess: boolean; isError: boolean; error: unknown }; success: string }) {
  if (mutation.isSuccess) {
    return <Alert sx={{ mt: 2 }} severity="success">{success}</Alert>
  }
  if (mutation.isError) {
    return <Alert sx={{ mt: 2 }} severity="error">{mutation.error instanceof Error ? mutation.error.message : 'Action failed.'}</Alert>
  }
  return null
}
