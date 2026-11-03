import { useEffect, useRef, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Checkbox, CircularProgress, Dialog, DialogContent, DialogTitle, Divider,
  FormControlLabel, IconButton, Stack, Typography } from '@mui/material'
import CloseRounded from '@mui/icons-material/CloseRounded'
import ShieldOutlined from '@mui/icons-material/ShieldOutlined'
import { changeSupportConsent, getSupportRequestContext, supportErrorMessage, updateSupportResources, type SupportRequestContext, type SupportResource } from '../../api/supportApi'
import { useCustomerSupport } from './CustomerSupportContext'
import { sameResource, SupportResourcePicker } from './SupportResourcePicker'

export function SupportResourceDialog() {
  const support = useCustomerSupport()
  return <Dialog open={support.diagnosticsOpen && !!support.requestId} onClose={() => support.setDiagnosticsOpen(false)} maxWidth="sm" fullWidth>
    <DialogTitle sx={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>Configure diagnostics
      <IconButton aria-label="Close diagnostics" onClick={() => support.setDiagnosticsOpen(false)}><CloseRounded /></IconButton>
    </DialogTitle>
    <DialogContent>{support.diagnosticsOpen && support.requestId && <ResourceContext key={support.requestId} requestId={support.requestId} />}</DialogContent>
  </Dialog>
}

function ResourceContext({ requestId }: { requestId: string }) {
  const support = useCustomerSupport()
  const client = useQueryClient()
  const key = ['support-request-context', support.ownerKey, requestId]
  const query = useQuery({ queryKey: key, queryFn: ({ signal }) => getSupportRequestContext(requestId, signal), retry: false, staleTime: 0, gcTime: 0 })
  const active = useRef(true)
  useEffect(() => { active.current = true; return () => { active.current = false } }, [])
  const [draft, setDraft] = useState<SupportResource[] | null>(null)
  const context = query.data
  const resources = draft ?? context?.resources ?? []
  const dirty = !!context && (resources.length !== context.resources.length || resources.some(resource => !context.resources.some(value => sameResource(resource, value))))
  const update = (value: SupportRequestContext) => { if (active.current) { client.setQueryData(key, value); setDraft(null) } }
  const changed = () => { if (active.current) void query.refetch() }
  const save = useMutation({ mutationFn: () => updateSupportResources(context!, resources), onSuccess: update, onError: changed, retry: false })
  const consent = useMutation({ mutationFn: (granted: boolean) => changeSupportConsent(context!, granted), onSuccess: update, onError: changed, retry: false })
  if (query.isPending) return <CircularProgress size={22} aria-label="Loading diagnostic permissions" />
  if (query.isError || !context) return <Alert severity="warning" action={<Button onClick={() => void query.refetch()}>Refresh</Button>}>{supportErrorMessage(query.error)}</Alert>
  const busy = save.isPending || consent.isPending || query.isFetching
  return <Stack spacing={2.5} sx={{ pt: 1 }}>
    <SupportResourcePicker customerId={support.customerId} value={resources} onChange={setDraft} disabled={busy} />
    <Button variant="outlined" disabled={!dirty || busy} onClick={() => save.mutate()}>Save resources</Button>
    <Typography variant="caption" color="text.secondary">Changing resources revokes status-check permission. Grant permission separately after saving.</Typography>
    <Divider />
    <Box><Stack direction="row" spacing={0.75} alignItems="center"><ShieldOutlined fontSize="small" color="secondary" /><Typography variant="subtitle2">Status-check permission</Typography></Stack>
      <FormControlLabel sx={{ alignItems: 'flex-start', m: 0 }} control={<Checkbox size="small" checked={context.consent.granted} disabled={busy || dirty} onChange={(_, granted) => consent.mutate(granted)} />}
        label={<Typography variant="body2" sx={{ pt: 0.85 }}>Allow AI and the integration team to check these selected resources for this request</Typography>} />
      <Typography variant="caption" color="text.secondary">Release and verification state, app readiness, and connection/indexing status only. No credentials, raw logs or business records. Documentation help remains available without permission.</Typography>
    </Box>
    {dirty && <Alert severity="info">Save resource changes before granting permission.</Alert>}
    {(save.error || consent.error) && <Alert severity="warning">{supportErrorMessage(save.error ?? consent.error)}</Alert>}
  </Stack>
}
