import { useEffect, useState } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Button, Chip, CircularProgress, Dialog, DialogActions, DialogContent, DialogTitle,
  IconButton, MenuItem, Stack, Table, TableBody, TableCell, TableContainer, TableHead, TableRow, TextField, Tooltip, Typography } from '@mui/material'
import AddOutlined from '@mui/icons-material/AddOutlined'
import RefreshOutlined from '@mui/icons-material/RefreshOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import ContentCopyOutlined from '@mui/icons-material/ContentCopyOutlined'
import KeyOutlined from '@mui/icons-material/KeyOutlined'
import { createCustomerDeploymentSecret, deleteCustomerDeploymentSecret, fetchCustomerDeploymentSecrets,
  rotateCustomerDeploymentSecret, type CustomerDeploymentSecret } from '../api/platformApi'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useBrainWorkspace } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'

const INTEGRATION = 'INTEGRATION_CREDENTIAL'
type Form = { name: string; purpose: string; value: string; secondaryValue: string }
const empty: Form = { name: '', purpose: INTEGRATION, value: '', secondaryValue: '' }

export function BrainSecretsPage() {
  const { brainId } = useBrainWorkspace()
  const { workspace } = useDeploymentWorkspace()
  const client = useQueryClient()
  const allowed = workspace?.access.canAdmin === true
  const [dialog, setDialog] = useState<'create' | 'rotate' | 'delete' | null>(null)
  const [selected, setSelected] = useState<CustomerDeploymentSecret | null>(null)
  const [form, setForm] = useState<Form>(empty)
  const [notice, setNotice] = useState<string | null>(null)
  const [copyError, setCopyError] = useState(false)
  const catalogue = useQuery({ queryKey: ['customer-deployment-secrets', brainId],
    queryFn: () => fetchCustomerDeploymentSecrets(brainId), enabled: allowed, retry: false })
  const paired = catalogue.data?.purposes.find(p => p.id === form.purpose)?.paired === true
  const nameInvalid = !/^[A-Z][A-Z0-9_]{0,63}$/.test(form.name)
  const close = () => { setDialog(null); setSelected(null); setForm(empty); mutation.reset() }
  const mutation = useMutation({
    mutationKey: ['customer-deployment-secret-write', brainId],
    mutationFn: async () => {
      if (dialog === 'create') return createCustomerDeploymentSecret(brainId, { ...form, secondaryValue: paired ? form.secondaryValue : undefined })
      if (dialog === 'rotate' && selected) return rotateCustomerDeploymentSecret(brainId, selected.name, form.value)
      if (dialog === 'delete' && selected) return deleteCustomerDeploymentSecret(brainId, selected.name)
      throw new Error('Select a key operation.')
    },
    onSuccess: async () => {
      const message = dialog === 'create' ? (form.purpose === INTEGRATION ? 'Key saved. Its reference is ready for your connection settings.' : 'Key saved and selected for this Smart Brain. Apply an update to refresh an existing runtime.')
        : dialog === 'rotate' ? 'Key replaced. Apply an update to refresh an existing runtime.' : 'Key deleted.'
      setForm(empty); setDialog(null); setSelected(null); setNotice(message)
      await client.invalidateQueries({ predicate: q => q.queryKey.includes(brainId) })
    },
  })
  useEffect(() => { setForm(empty); setSelected(null); setDialog(null); setNotice(null); setCopyError(false) }, [brainId])
  const open = (mode: 'create' | 'rotate' | 'delete', secret: CustomerDeploymentSecret | null = null) => {
    mutation.reset(); setNotice(null); setCopyError(false); setForm(empty); setSelected(secret); setDialog(mode)
  }
  const copy = async (secret: CustomerDeploymentSecret) => {
    try { await navigator.clipboard.writeText(secret.name); setNotice('Secret reference copied. No key value was copied.'); setCopyError(false) }
    catch { setCopyError(true) }
  }
  return <>
    <BrainPageHeader stackOnMobile title="Keys & secrets" description="Credentials for this Smart Brain's AI services and integrations."
      action={<Button startIcon={<AddOutlined />} variant="contained" disabled={!allowed || !catalogue.isSuccess} onClick={() => open('create')}>Add key</Button>} />
    <Stack gap={2} sx={{ ...brainPaneSx, flex: 1 }}>
      {!allowed ? <Alert severity="info">A workspace owner or delegated administrator can manage these keys.</Alert>
        : catalogue.isPending ? <CircularProgress size={24} aria-label="Loading keys" />
          : catalogue.isError ? <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{catalogue.error.message}</Alert>
            : <>
              {notice && <Alert severity="success" onClose={() => setNotice(null)}>{notice}</Alert>}
              {copyError && <Alert severity="warning" onClose={() => setCopyError(false)}>Clipboard access is unavailable. Select the displayed reference to copy it.</Alert>}
              <Stack direction={{ xs: 'column', sm: 'row' }} gap={2} alignItems={{ sm: 'center' }} justifyContent="space-between" sx={{ py: 1, borderBottom: 1, borderColor: 'divider' }}>
                <Typography variant="body2" color="text.secondary">Values stay hidden after saving. The AI assistant receives references only.</Typography>
                <Tooltip title="Refresh key status"><IconButton aria-label="Refresh keys" onClick={() => void catalogue.refetch()} disabled={catalogue.isFetching}><RefreshOutlined fontSize="small" /></IconButton></Tooltip>
              </Stack>
              {catalogue.data.secrets.length === 0 ? <Box sx={{ py: 6, textAlign: 'center' }}>
                <KeyOutlined color="action" sx={{ fontSize: 32, mb: 1 }} /><Typography fontWeight={600}>No customer keys yet</Typography>
                <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>Add your provider key or save a key for an API connection.</Typography>
              </Box> : <TableContainer sx={{ flexShrink: 0 }}><Table aria-label="Smart Brain keys" size="small">
                <TableHead><TableRow><TableCell>Key</TableCell><TableCell>Used for</TableCell><TableCell>Status</TableCell><TableCell>Updated</TableCell><TableCell align="right">Actions</TableCell></TableRow></TableHead>
                <TableBody>{catalogue.data.secrets.map(secret => <TableRow key={secret.name} hover>
                  <TableCell sx={{ minWidth: 180, maxWidth: 340 }}><Typography variant="body2" fontWeight={600}>{secret.label}</Typography>
                    <Typography component="code" sx={{ display: 'block', fontSize: 11, color: 'text.secondary', overflowWrap: 'anywhere', userSelect: 'text', mt: .5 }}>{secret.name}</Typography></TableCell>
                  <TableCell>{catalogue.data.purposes.find(p => p.id === secret.purpose)?.label ?? secret.purpose}</TableCell>
                  <TableCell><Chip size="small" variant="outlined" color={secret.present ? 'success' : 'warning'} label={secret.bindingCount > 0 ? 'Provider selected' : secret.present ? 'Saved' : 'Missing'} /></TableCell>
                  <TableCell sx={{ whiteSpace: 'nowrap' }}>{secret.updatedAt ? new Date(secret.updatedAt).toLocaleDateString() : 'Unknown'}</TableCell>
                  <TableCell align="right"><Stack direction="row" justifyContent="flex-end">
                    <Tooltip title="Copy reference"><IconButton aria-label={`Copy reference for ${secret.label}`} onClick={() => void copy(secret)}><ContentCopyOutlined fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title="Replace key value"><IconButton aria-label={`Replace ${secret.label}`} onClick={() => open('rotate', secret)}><RefreshOutlined fontSize="small" /></IconButton></Tooltip>
                    <Tooltip title={secret.bindingCount > 0 ? 'Save a replacement provider key before deleting this one' : 'Delete key'}><span><IconButton color="error" aria-label={`Delete ${secret.label}`} disabled={secret.bindingCount > 0} onClick={() => open('delete', secret)}><DeleteOutline fontSize="small" /></IconButton></span></Tooltip>
                  </Stack></TableCell>
                </TableRow>)}</TableBody>
              </Table></TableContainer>}
              <Typography variant="caption" color="text.secondary">Keys belong to this Smart Brain, not other customers or Brains. Staging and production use its selected credentials. LoomAI infrastructure and signing keys remain platform-managed. Separate credentials are still required by target-specific storage and vector bindings.</Typography>
            </>}
    </Stack>
    <Dialog open={dialog !== null} onClose={() => { if (!mutation.isPending) close() }} fullWidth maxWidth="sm" disableRestoreFocus={false}>
      <DialogTitle>{dialog === 'create' ? 'Add a key' : dialog === 'rotate' ? `Replace ${selected?.label ?? 'key'}` : `Delete ${selected?.label ?? 'key'}?`}</DialogTitle>
      <DialogContent><Stack gap={2} sx={{ pt: 1 }}>
        {mutation.isError && <Alert severity="error">{mutation.error.message}</Alert>}
        {dialog === 'create' && <>
          <TextField select label="Used for" value={form.purpose} onChange={e => setForm(f => ({ ...f, purpose: e.target.value, value: '', secondaryValue: '' }))} disabled={mutation.isPending}>
            {catalogue.data?.purposes.map(p => <MenuItem key={p.id} value={p.id}>{p.label}</MenuItem>)}
          </TextField>
          <TextField autoFocus required label="Key name" placeholder="MY_API_KEY" value={form.name} onChange={e => setForm(f => ({ ...f, name: e.target.value.toUpperCase() }))}
            error={form.name.length > 0 && nameInvalid} helperText="A short reference name, for example SERVICE_API_KEY. Uppercase letters, numbers and underscores; start with a letter."
            inputProps={{ maxLength: 64 }} disabled={mutation.isPending} autoComplete="off" />
          {form.purpose !== INTEGRATION && <Alert severity="info">This selects your key for the provider on this Smart Brain. It does not change the selected AI model or vector service. An existing runtime needs an explicit update.</Alert>}
        </>}
        {dialog !== 'delete' && <TextField required label={paired && dialog === 'create' ? 'Username' : 'Key value'} type="password" value={form.value}
          onChange={e => setForm(f => ({ ...f, value: e.target.value }))} autoComplete="new-password" inputProps={{ maxLength: 16384 }} disabled={mutation.isPending}
          helperText="Write-only. The saved value cannot be viewed or copied back." />}
        {paired && dialog === 'create' && <TextField required label="Password" type="password" value={form.secondaryValue}
          onChange={e => setForm(f => ({ ...f, secondaryValue: e.target.value }))} autoComplete="new-password" inputProps={{ maxLength: 16384 }} disabled={mutation.isPending} />}
        {dialog === 'delete' && <Alert severity="warning">Any connection using this reference will stop working when it next loads credentials. This cannot be undone.</Alert>}
      </Stack></DialogContent>
      <DialogActions><Button disabled={mutation.isPending} onClick={close}>Cancel</Button><Button variant="contained" color={dialog === 'delete' ? 'error' : 'primary'}
        disabled={mutation.isPending || dialog !== 'delete' && (!form.value.trim() || dialog === 'create' && (nameInvalid || paired && !form.secondaryValue.trim()))}
        onClick={() => mutation.mutate()}>{mutation.isPending ? 'Saving...' : dialog === 'delete' ? 'Delete key' : dialog === 'rotate' ? 'Replace value' : form.purpose === INTEGRATION ? 'Save key' : 'Save & use key'}</Button></DialogActions>
    </Dialog>
  </>
}
