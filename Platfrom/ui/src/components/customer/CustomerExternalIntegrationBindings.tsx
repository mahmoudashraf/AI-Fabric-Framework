import SaveRoundedIcon from '@mui/icons-material/SaveRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import LinkOffRoundedIcon from '@mui/icons-material/LinkOffRounded'
import { Alert, Box, Button, Dialog, DialogActions, DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useMutation } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { bindCustomerExternalIntegration, fetchCustomerDeploymentPortfolio, fetchCustomerExternalIntegration,
  fetchCustomerExternalIntegrationApprovals, fetchCustomerSelfServiceOffer, fetchDeploymentVersions,
  fetchPlatformAuthSession, PlatformApiError, revokeCustomerExternalIntegration,
  type CustomerDeploymentSummary, type CustomerExternalIntegrationApprovalOption, type CustomerExternalIntegrationSummary,
  type CustomerOfferSummary, type DeploymentVersionSummary, type PlatformAuthSessionSummary } from '../../api/platformApi'
import { checkedExternalApproval, checkedExternalBinding, externalConnectionProfile, requireFreshExternalAuthority,
  type ExternalConnectionAuthority } from './customerExternalIntegration'

type Props = { session: PlatformAuthSessionSummary; offer: CustomerOfferSummary; deployment: CustomerDeploymentSummary; version: DeploymentVersionSummary | null }
const errorText = (error: unknown) => error instanceof Error ? error.message : 'The connection operation failed.'

export function CustomerExternalIntegrationBindings({ session, offer, deployment, version }: Props) {
  const slots = offer.bindingSlots.filter(item => item.kind === 'EXTERNAL_SERVICE')
  const [targetId, setTargetId] = useState(deployment.bootstrapTargetProfileId)
  const [slotId, setSlotId] = useState(slots[0]?.id ?? '')
  const [profileId, setProfileId] = useState('')
  if (!slots.length) return null
  const profiles = (offer.externalIntegrationProfiles ?? []).filter(item => item.bindingSlotId === slotId
    && item.targetProfileIds.includes(targetId))
  const profile = profiles.find(item => item.id === profileId) ?? (profiles.length === 1 ? profiles[0] : null)
  const scope = { versionId: version?.id ?? '', targetProfileId: targetId, bindingSlotId: slotId, selectedProfileId: profile?.id ?? '' }
  const key = JSON.stringify([session.actorId, session.authenticationMode, session.customerId, session.role, session.authenticated,
    offer.id, offer.fingerprint, offer.entitlement, deployment.deploymentId, deployment.tenantId, version?.id, version?.configHash, scope, profile])
  return <Stack spacing={2} component="section" aria-label="Approved connections" id="approved-connections">
    <Typography variant="h6">Approved connections</Typography>
    {!version ? <Alert severity="info">No published version is available for connection setup.</Alert> : <>
      <Typography variant="caption" color="text.secondary">{version.versionLabel} / {version.id}</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={2}>
        <TextField select fullWidth label="Connection target" value={targetId} onChange={e => { setTargetId(e.target.value); setProfileId('') }}>
          {offer.targets.filter(item => item.capabilities.includes('READ')).map(item => <MenuItem key={item.id} value={item.id}>{item.intent === 'CUSTOMER_STAGING' ? 'Customer staging' : 'Customer production'} / {item.id}</MenuItem>)}
        </TextField>
        <TextField select fullWidth label="Connection slot" value={slotId} onChange={e => { setSlotId(e.target.value); setProfileId('') }}>
          {slots.map(item => <MenuItem key={item.id} value={item.id}>{item.id}</MenuItem>)}
        </TextField>
        <TextField select fullWidth label="Connection profile" value={profile?.id ?? ''} onChange={e => setProfileId(e.target.value)}>
          {profiles.map(item => <MenuItem key={item.id} value={item.id}>{item.id}</MenuItem>)}
        </TextField>
      </Stack>
      {profile ? <ConnectionForm key={key} authority={{ session, offer, deployment, version, scope }} />
        : <Alert severity="warning">No reviewed connection profile is selected for this target.</Alert>}
    </>}
  </Stack>
}

function ConnectionForm({ authority }: { authority: ExternalConnectionAuthority }) {
  const alive = useRef(true)
  useEffect(() => { alive.current = true; return () => { alive.current = false } }, [])
  const [credentials, setCredentials] = useState<Record<string, string>>({})
  const [approvalId, setApprovalId] = useState('')
  const [result, setResult] = useState<{ options: CustomerExternalIntegrationApprovalOption[]; binding: CustomerExternalIntegrationSummary | null } | null>(null)
  const [confirmRevoke, setConfirmRevoke] = useState(false)
  const operationKey = useRef<string | null>(null)
  const { offer, deployment, scope } = authority
  let profile: ReturnType<typeof externalConnectionProfile> | null = null
  let authorityError: string | null = null
  try { profile = externalConnectionProfile(authority) } catch (error) { authorityError = errorText(error) }
  const fresh = (operation: 'READ' | 'BINDING_REPLACE' | 'BINDING_REVOKE') => requireFreshExternalAuthority(authority, {
    session: fetchPlatformAuthSession, offer: fetchCustomerSelfServiceOffer,
    deployments: fetchCustomerDeploymentPortfolio, versions: fetchDeploymentVersions,
  }, () => alive.current, operation)
  const read = async () => {
    const options = await fetchCustomerExternalIntegrationApprovals(offer.id, deployment.deploymentId, scope)
    const checked = options.map(option => checkedExternalApproval(scope, option))
    let binding: CustomerExternalIntegrationSummary | null = null
    try { binding = checkedExternalBinding(deployment.deploymentId, scope, await fetchCustomerExternalIntegration(offer.id, deployment.deploymentId, scope)) }
    catch (error) { if (!(error instanceof PlatformApiError && error.status === 404)) throw error }
    return { options: checked, binding }
  }
  const review = useMutation({ gcTime: 0, mutationFn: async () => {
    await fresh('READ')
    if (!alive.current) throw new Error('The selected connection changed.')
    return read()
  }, onSuccess: data => { if (alive.current) setResult(data) } })
  const bind = useMutation({ gcTime: 0, mutationFn: async () => {
    const approval = result?.options.find(item => item.id === approvalId)
    if (!approval || !profile) throw new Error('Select a current reviewed connection.')
    checkedExternalApproval(scope, approval)
    const values = Object.fromEntries(profile.secretFields.map(field => [field, credentials[field] ?? '']))
    if (Object.values(values).some(value => !value.trim())) throw new Error('All required credentials must be supplied.')
    const key = operationKey.current ??= crypto.randomUUID()
    await fresh('BINDING_REPLACE')
    if (!alive.current) throw new Error('The selected connection changed.')
    return checkedExternalBinding(deployment.deploymentId, scope, await bindCustomerExternalIntegration(offer.id, deployment.deploymentId, {
      ...scope, approvalId, credentials: values, expectedEntitlementRevision: offer.entitlement.revision, operationKey: key,
    }))
  }, onSuccess: binding => {
    if (!alive.current) return
    setResult(current => current ? { ...current, binding } : null); setCredentials({}); operationKey.current = null
  } })
  const revoke = useMutation({ gcTime: 0, mutationFn: async () => {
    if (!result?.binding) throw new Error('No current binding is selected.')
    const binding = checkedExternalBinding(deployment.deploymentId, scope, result.binding)
    const key = operationKey.current ??= crypto.randomUUID()
    await fresh('BINDING_REVOKE')
    if (!alive.current) throw new Error('The selected connection changed.')
    return checkedExternalBinding(deployment.deploymentId, scope, await revokeCustomerExternalIntegration(offer.id, deployment.deploymentId, {
      ...scope, bindingId: binding.id, expectedBindingRevision: binding.revision,
      expectedEntitlementRevision: offer.entitlement.revision, operationKey: key,
    }))
  }, onSuccess: binding => {
    if (!alive.current) return
    setResult(current => current ? { ...current, binding } : null); setConfirmRevoke(false); setCredentials({}); operationKey.current = null
  } })
  const busy = review.isPending || bind.isPending || revoke.isPending
  const selected = result?.options.find(item => item.id === approvalId)
  const bound = ['CONFIGURED', 'CONFIGURED_LOCAL_CREDENTIALS'].includes(result?.binding?.status ?? '')
  const can = (operation: 'BINDING_REPLACE' | 'BINDING_REVOKE') => offer.targets.some(target => target.id === scope.targetProfileId && target.capabilities.includes(operation))
  return <Stack spacing={2}>
    {authorityError ? <Alert severity="error">{authorityError}</Alert> : null}
    <Box><Button startIcon={<RefreshRoundedIcon />} variant="outlined" disabled={busy || Boolean(authorityError)} onClick={() => {
      setResult(null); setApprovalId(''); setCredentials({}); operationKey.current = null; bind.reset(); revoke.reset(); review.mutate()
    }}>Review approved connection</Button></Box>
    {[review.error, bind.error, revoke.error].filter(Boolean).map((error, index) => <Alert key={index} severity="error">{errorText(error)}</Alert>)}
    {result?.binding ? <Alert severity={bound ? 'success' : 'info'}>Connection {result.binding.status.toLowerCase()} / {result.binding.id}</Alert> : null}
    {result && !result.options.length ? <Alert severity="warning">No approved connection is available for this version and target. Contact your Platform administrator.</Alert> : null}
    {result?.binding && !bound ? <Alert severity="warning">This version already has an immutable binding record. Publish a new version to configure a replacement.</Alert> : null}
    {result && result.options.length > 0 && !result.binding ? <>
      <TextField select label="Approved connection" value={approvalId} disabled={busy} onChange={e => { setApprovalId(e.target.value); setCredentials({}); operationKey.current = null; bind.reset() }}>
        {result.options.map(option => <MenuItem key={option.id} value={option.id}>{option.reviewReference} / {option.id}</MenuItem>)}
      </TextField>
      {selected ? <>
        <Box component="dl" sx={{ m: 0, overflowWrap: 'anywhere' }}>{Object.entries({ ...selected.config, ...selected.accountScope }).map(([name, value]) => <Box key={name} sx={{ py: 0.5 }}><Typography component="dt" variant="caption" color="text.secondary">{name}</Typography><Typography component="dd" variant="body2" sx={{ m: 0 }}>{value}</Typography></Box>)}</Box>
        {profile?.secretFields.map(field => <TextField key={field} label={field} type="password" value={credentials[field] ?? ''} autoComplete="new-password" disabled={busy}
          onChange={e => { setCredentials(current => ({ ...current, [field]: e.target.value })); operationKey.current = null; bind.reset() }} />)}
        <Box><Button startIcon={<SaveRoundedIcon />} variant="contained" disabled={busy || !can('BINDING_REPLACE') || Boolean(authorityError)
          || profile?.secretFields.some(field => !credentials[field]?.trim())} onClick={() => bind.mutate()}>Bind approved connection</Button></Box>
      </> : null}
    </> : null}
    {bound ? <Box><Button startIcon={<LinkOffRoundedIcon />} color="error" disabled={busy || !can('BINDING_REVOKE')} onClick={() => { operationKey.current = null; setConfirmRevoke(true) }}>Revoke connection</Button></Box> : null}
    <Dialog open={confirmRevoke} onClose={() => !busy && setConfirmRevoke(false)} maxWidth="sm" fullWidth>
      <DialogTitle>Revoke this connection?</DialogTitle><DialogContent><Typography sx={{ overflowWrap: 'anywhere' }}>{deployment.name} / {scope.targetProfileId} / {scope.versionId} / {scope.bindingSlotId}</Typography>{revoke.error ? <Alert severity="error" sx={{ mt: 2 }}>{errorText(revoke.error)}</Alert> : null}</DialogContent>
      <DialogActions><Button disabled={busy} onClick={() => setConfirmRevoke(false)}>Cancel</Button><Button color="error" disabled={busy || !can('BINDING_REVOKE')} onClick={() => revoke.mutate()}>Confirm revoke</Button></DialogActions>
    </Dialog>
  </Stack>
}
