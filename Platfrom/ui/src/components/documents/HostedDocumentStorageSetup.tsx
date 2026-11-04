import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { Link as RouterLink } from 'react-router-dom'
import { Alert, Box, Button, Checkbox, Chip, CircularProgress, FormControlLabel, IconButton, Link,
  MenuItem, Stack, TextField, ToggleButton, ToggleButtonGroup, Tooltip, Typography } from '@mui/material'
import AddRounded from '@mui/icons-material/AddRounded'
import CloudDoneOutlined from '@mui/icons-material/CloudDoneOutlined'
import CloudUploadOutlined from '@mui/icons-material/CloudUploadOutlined'
import LinkRounded from '@mui/icons-material/LinkRounded'
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded'
import RefreshRounded from '@mui/icons-material/RefreshRounded'
import StorageOutlined from '@mui/icons-material/StorageOutlined'
import { usePlatformAuth } from '../../auth/PlatformAuthProvider'
import type { DocumentStorageBindingSummary } from '../../api/platformApi'
import { bindHostedDocumentStorage, createHostedDocumentStorage, documentStorageKeys, fetchHostedDocumentStorage,
  fetchHostedDocumentUploads, fetchHostedDocumentSelections, prepareHostedDocumentSelection, prepareHostedDocumentStorage, uploadHostedDocument,
  type DocumentStorageSelectionItem, type PreparedDocumentSelection } from '../../api/documentStorageApi'

type TargetChoice = { id: string; name: string }
type RuntimeTarget = { targetProfileId: string; releaseId: string; datasetId: string }
export type HostedDocumentStorageSetupProps = {
  deploymentId: string
  customerId?: string
  targetProfiles: TargetChoice[]
  initialTargetProfileId?: string
  bindings?: DocumentStorageBindingSummary[]
  runtime?: RuntimeTarget
  canManage?: boolean
  defaultMode?: 'hosted' | 'byo'
  byoStorage: ReactNode
  indexingHref?: string
}

type Command = { identity: string; view: string; operationKey: string } & (
  | { kind: 'create'; name: string }
  | { kind: 'prepare'; connectionId: string }
  | { kind: 'upload'; connectionId: string; file: File; prefix: string }
  | { kind: 'bind'; connectionId: string; targetProfileId: string; prefix: string }
  | { kind: 'selection'; connectionId: string; runtime: RuntimeTarget; uploadIds: string[]; publicApproved: boolean }
)
type CommandInput = Command extends infer C ? C extends Command ? Omit<C, 'identity' | 'view' | 'operationKey'> : never : never

const message = (error: unknown) => error instanceof Error ? error.message : 'The storage operation failed.'
const bytes = (size: number) => size < 1024 ? `${size} B` : size < 1048576 ? `${(size / 1024).toFixed(1)} KB` : `${(size / 1048576).toFixed(1)} MB`

// These checks protect the upload size/type/path execution contract, not document meaning.
export function hostedUploadError(file: File | null, maxBytes: number, extensions: string[]): string | null {
  if (!file) return 'Choose a file.'
  if (!file.size) return 'The file is empty.'
  if (file.size > maxBytes) return `The file exceeds the ${bytes(maxBytes)} limit.`
  const extension = file.name.includes('.') ? file.name.split('.').pop()!.toLowerCase() : ''
  if (!extensions.includes(extension)) return `Supported files: ${extensions.map(value => `.${value}`).join(', ')}.`
  return null
}

export function hostedPrefixError(prefix: string): string | null {
  if (!prefix || prefix.startsWith('/') || prefix.includes('\\') || prefix.includes('%')
    || /[\u0000-\u001f\u007f]/.test(prefix) || prefix.split('/').some(part => part === '.' || part === '..')) {
    return 'Enter a relative purpose prefix, such as public/, without traversal or encoded paths.'
  }
  return null
}

export function HostedDocumentStorageSetup(props: HostedDocumentStorageSetupProps) {
  const { session } = usePlatformAuth()
  const scope = JSON.stringify([session?.actorId, session?.authenticationMode, session?.role, session?.customerId, props.customerId])
  if (!session?.authenticated || !props.deploymentId || (props.customerId && session.customerId !== props.customerId)) return null
  // Remount all local forms, receipts and retry identities at an authority/deployment boundary.
  return <StorageSetupScope key={`${scope}:${props.deploymentId}`} {...props} scope={scope} />
}

function StorageSetupScope({ deploymentId, targetProfiles, initialTargetProfileId = '', bindings = [], runtime,
  canManage = true, defaultMode = 'hosted', byoStorage, indexingHref, scope }: HostedDocumentStorageSetupProps & { scope: string }) {
  const client = useQueryClient()
  const fieldId = useId()
  const [mode, setMode] = useState(defaultMode)
  const [connectionId, setConnectionId] = useState('')
  const [name, setName] = useState('Knowledge files')
  const [prefix, setPrefix] = useState('public/')
  const [targetId, setTargetId] = useState(initialTargetProfileId)
  const [file, setFile] = useState<File | null>(null)
  const [fileIdentity, setFileIdentity] = useState('')
  const [selectedIds, setSelectedIds] = useState<string[]>([])
  const [publicApproved, setPublicApproved] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const active = useRef(true)
  const controllers = useRef(new Set<AbortController>())
  const operations = useRef(new Map<string, string>())
  useEffect(() => {
    active.current = true
    return () => { active.current = false; controllers.current.forEach(controller => controller.abort()) }
  }, [])
  useEffect(() => { setPublicApproved(false); setSelectedIds([]) }, [runtime?.targetProfileId, runtime?.releaseId, runtime?.datasetId])
  useEffect(() => {
    if (!targetId && initialTargetProfileId) setTargetId(initialTargetProfileId)
  }, [initialTargetProfileId, targetId])

  const connectionsKey = documentStorageKeys.connections(scope, deploymentId)
  const catalogue = useQuery({ queryKey: connectionsKey,
    queryFn: ({ signal }) => fetchHostedDocumentStorage(deploymentId, signal), retry: false, enabled: mode === 'hosted' })
  const connection = catalogue.data?.connections.find(item => item.id === connectionId)
  const uploadsKey = documentStorageKeys.uploads(scope, deploymentId, connectionId)
  const uploads = useQuery({ queryKey: uploadsKey, queryFn: ({ signal }) => fetchHostedDocumentUploads(deploymentId, connectionId, signal),
    enabled: mode === 'hosted' && Boolean(connection), retry: false })
  const target = targetProfiles.find(item => item.id === targetId)
  const purposePrefix = prefix.trim().endsWith('/') ? prefix.trim() : `${prefix.trim()}/`
  const currentPrefixError = hostedPrefixError(prefix.trim())
  const ready = connection?.status === 'READY'
  const view = JSON.stringify([connectionId, targetId, purposePrefix, runtime?.targetProfileId, runtime?.releaseId, runtime?.datasetId])
  const currentView = useRef(view)
  currentView.current = view
  const liveSelection = Boolean(runtime?.releaseId && runtime.datasetId && runtime.targetProfileId === targetId)
  const selectionsKey = documentStorageKeys.selections(scope, deploymentId, connectionId, runtime?.targetProfileId ?? '', runtime?.releaseId ?? '')
  const selections = useQuery({ queryKey: selectionsKey,
    queryFn: ({ signal }) => fetchHostedDocumentSelections(deploymentId, connectionId, runtime!.targetProfileId, runtime!.releaseId, signal),
    enabled: mode === 'hosted' && Boolean(connection) && liveSelection, retry: false })
  const invalidateBindings = () => {
    for (const key of ['document-storage-bindings', 'deployment-marketplace-installs', 'deployment-draft', 'deployment-workspace']) {
      void client.invalidateQueries({ queryKey: [key, deploymentId] })
    }
  }
  const mutation = useMutation({
    mutationFn: async (command: Command) => {
      const controller = new AbortController()
      controllers.current.add(controller)
      try {
        const signal = controller.signal
        switch (command.kind) {
          case 'create': return await createHostedDocumentStorage(deploymentId, { name: command.name, operationKey: command.operationKey }, signal)
          case 'prepare': return await prepareHostedDocumentStorage(deploymentId, command.connectionId, { operationKey: command.operationKey }, signal)
          case 'upload': return await uploadHostedDocument(deploymentId, command.connectionId, command, signal)
          case 'bind': return await bindHostedDocumentStorage(deploymentId, command.connectionId, {
            targetProfileId: command.targetProfileId, prefix: command.prefix, operationKey: command.operationKey,
          }, signal)
          case 'selection': return await prepareHostedDocumentSelection(deploymentId, command.connectionId, {
            targetProfileId: command.runtime.targetProfileId, releaseId: command.runtime.releaseId, datasetId: command.runtime.datasetId,
            uploadIds: command.uploadIds, publicApproved: command.publicApproved, operationKey: command.operationKey,
          }, signal)
        }
      } finally { controllers.current.delete(controller) }
    },
    onSuccess: async (result, command) => {
      if (!active.current) return
      // Transport retries retain the key; a successful explicit next check is a new reconciliation command.
      operations.current.delete(command.identity)
      void client.invalidateQueries({ queryKey: connectionsKey })
      if (command.kind === 'upload') void client.invalidateQueries({ queryKey: documentStorageKeys.uploads(scope, deploymentId, command.connectionId) })
      if (command.kind === 'bind') invalidateBindings()
      if (command.kind === 'selection') {
        const key = documentStorageKeys.selections(scope, deploymentId, command.connectionId, command.runtime.targetProfileId, command.runtime.releaseId)
        await client.cancelQueries({ queryKey: key, exact: true })
        if (!active.current) return
        const items = (result as PreparedDocumentSelection).items
        client.setQueryData<DocumentStorageSelectionItem[]>(key, previous => [
          ...(previous ?? []).filter(item => !items.some(fresh => fresh.uploadId === item.uploadId)), ...items,
        ])
        void client.invalidateQueries({ queryKey: key, exact: true })
        for (const key of ['document-sources', 'document-source-detail', 'document-connector-status']) {
          void client.invalidateQueries({ queryKey: [key, deploymentId, command.runtime.targetProfileId, command.runtime.releaseId] })
        }
      }
      if (currentView.current !== command.view) return
      if (command.kind === 'create' && 'id' in result) setConnectionId(result.id)
      if (command.kind === 'upload') { setFile(null); if (input.current) input.current.value = '' }
    },
  })
  const busy = mutation.isPending
  const submit = (command: CommandInput, identity: unknown[]) => {
    if (!canManage || busy) return
    const fingerprint = JSON.stringify(identity)
    let operationKey = operations.current.get(fingerprint)
    if (!operationKey) { operationKey = crypto.randomUUID(); operations.current.set(fingerprint, operationKey) }
    mutation.mutate({ ...command, identity: fingerprint, view, operationKey } as Command)
  }
  const currentCommand = mutation.variables?.view === view
  const selection = currentCommand && mutation.variables?.kind === 'selection' && mutation.isSuccess
    ? mutation.data as PreparedDocumentSelection : null
  const eligibleIds = selectedIds.filter(id => uploads.data?.some(upload => upload.id === id && upload.connectionId === connectionId
    && upload.status === 'UPLOADED' && upload.prefix === purposePrefix))
  const uploadError = file && catalogue.data ? hostedUploadError(file, catalogue.data.maxUploadBytes, catalogue.data.supportedExtensions) : null
  const selectedBindings = bindings.filter(binding => binding.safeMetadata?.storageConnectionId === connectionId)
  const boundResult = currentCommand && mutation.variables?.kind === 'bind' && mutation.isSuccess && mutation.data && 'binding' in mutation.data
    ? mutation.data.binding : null
  const visibleBindings = boundResult && !selectedBindings.some(binding => binding.bindingRef === boundResult.bindingRef)
    ? [...selectedBindings, boundResult] : selectedBindings

  return <Box component="section" aria-label="Document storage setup" sx={{ minWidth: 0, '& .MuiButton-root': { whiteSpace: 'normal' }, '& .MuiAlert-message': { minWidth: 0, overflowWrap: 'anywhere' } }}>
    <Stack spacing={2}>
      <Typography variant="h6" component="h2">Document storage</Typography>
      <ToggleButtonGroup exclusive size="small" value={mode} aria-label="Document storage provider"
        onChange={(_, value) => { if (value) setMode(value) }} disabled={busy}
        sx={{ display: 'grid', gridTemplateColumns: 'repeat(2, minmax(0, 1fr))', alignSelf: { xs: 'stretch', sm: 'flex-start' }, '& .MuiToggleButton-root': { minHeight: 44, px: 1 } }}>
        <ToggleButton value="hosted"><CloudDoneOutlined fontSize="small" sx={{ mr: 1 }} />Use LoomAI storage</ToggleButton>
        <ToggleButton value="byo"><StorageOutlined fontSize="small" sx={{ mr: 1 }} />Connect my storage</ToggleButton>
      </ToggleButtonGroup>
      {mode === 'byo' ? byoStorage : <>
        {catalogue.isPending && <Stack direction="row" gap={1} role="status"><CircularProgress size={18} />Loading storage connections...</Stack>}
        {catalogue.isError && <Alert severity="error" action={<Button onClick={() => void catalogue.refetch()}>Retry</Button>}>{message(catalogue.error)}</Alert>}
        {catalogue.data && <>
          {!catalogue.data.hostedAvailable && <Alert severity="info">Hosted storage changes are unavailable. Existing connections remain listed; bring-your-own storage is still supported.</Alert>}
          <TextField select fullWidth size="small" id={`${fieldId}-connection`} label="Hosted storage connection"
            SelectProps={{ SelectDisplayProps: { 'aria-labelledby': `${fieldId}-connection-label` } }} value={connection ? connectionId : ''} disabled={busy}
            onChange={event => { setConnectionId(event.target.value); setSelectedIds([]); setPublicApproved(false); setFile(null); if (input.current) input.current.value = '' }}>
            <MenuItem value="">Select a connection</MenuItem>
            {catalogue.data.connections.map(item => <MenuItem key={item.id} value={item.id} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{item.name} ({item.status})</MenuItem>)}
          </TextField>
          <Stack component="form" direction={{ xs: 'column', sm: 'row' }} gap={1} onSubmit={event => {
            event.preventDefault(); if (name.trim() && catalogue.data.hostedAvailable) submit({ kind: 'create', name: name.trim() }, ['create', name.trim()])
          }}>
            <TextField size="small" label="New connection name" value={name} inputProps={{ maxLength: 160 }} onChange={event => setName(event.target.value)} disabled={!canManage || busy || !catalogue.data.hostedAvailable} sx={{ flex: 1 }} />
            <Button type="submit" variant="outlined" startIcon={<AddRounded />} disabled={!canManage || busy || !name.trim() || !catalogue.data.hostedAvailable}>Create connection</Button>
          </Stack>
          {connection && <>
            <Stack direction="row" gap={1} alignItems="center" flexWrap="wrap">
              <Chip size="small" label={ready ? 'Storage ready' : connection.status} color={ready ? 'success' : 'default'} />
              <Tooltip title="Refresh storage, uploads and indexing status"><IconButton size="small" aria-label="Refresh hosted storage" disabled={busy} onClick={() => { void catalogue.refetch(); void uploads.refetch(); if (liveSelection) void selections.refetch() }}><RefreshRounded /></IconButton></Tooltip>
              <Button size="small" variant="outlined" startIcon={<CloudDoneOutlined />} disabled={!canManage || busy || ready || !catalogue.data.hostedAvailable}
                onClick={() => submit({ kind: 'prepare', connectionId }, ['prepare', connectionId])}>Prepare storage</Button>
            </Stack>
            <Typography variant="body2" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>
              {connection.bucket ? `${ready ? 'Bucket' : 'Planned bucket'}: ${connection.bucket} | Region: ${connection.region} | Root: ${connection.rootPrefix}` : 'Configuration saved. Storage has not been prepared.'}
            </Typography>
            {connection.lastError && <Alert severity="error">{connection.lastError}</Alert>}
            <TextField fullWidth size="small" label="Purpose prefix" value={prefix} inputProps={{ maxLength: 480 }} disabled={!canManage || busy}
              onChange={event => { setPrefix(event.target.value); setSelectedIds([]); setPublicApproved(false) }} error={Boolean(currentPrefixError)}
              helperText={currentPrefixError ?? `Relative to ${connection.rootPrefix || 'knowledge/'}; public approval is separate.`} />
            <Stack direction={{ xs: 'column', sm: 'row' }} gap={1}>
              <TextField select fullWidth size="small" id={`${fieldId}-target`} label="Storage target"
                SelectProps={{ SelectDisplayProps: { 'aria-labelledby': `${fieldId}-target-label` } }} value={target ? targetId : ''} disabled={!canManage || busy} sx={{ flex: 1 }}
                onChange={event => { setTargetId(event.target.value); setSelectedIds([]); setPublicApproved(false) }}>
                <MenuItem value="">Select an allowed target</MenuItem>
                {targetProfiles.map(profile => <MenuItem key={profile.id} value={profile.id}>{profile.name}</MenuItem>)}
              </TextField>
              <Button variant="outlined" startIcon={<LinkRounded />} sx={{ flexShrink: 0 }} disabled={!canManage || busy || !ready || !target || !catalogue.data.hostedAvailable || Boolean(currentPrefixError)}
                onClick={() => submit({ kind: 'bind', connectionId, targetProfileId: targetId, prefix: purposePrefix }, ['bind', connectionId, targetId, purposePrefix])}>Bind storage</Button>
            </Stack>
            {!targetProfiles.length && <Alert severity="info">No allowed storage targets are available.</Alert>}
            {visibleBindings.map(binding => <Typography key={binding.bindingRef} variant="body2" role="status" sx={{ overflowWrap: 'anywhere' }}>
              {targetProfiles.find(profile => profile.id === binding.targetProfileId)?.name ?? binding.targetProfileId}: {binding.status}{binding.credentialsPresent ? ' | Read-only binding configured' : ' | Reader credentials missing'}
              {typeof binding.safeMetadata.prefix === 'string' ? ` | ${binding.safeMetadata.prefix}` : ''}
            </Typography>)}
            <Stack component="form" spacing={1} onSubmit={event => {
              event.preventDefault()
              if (file && ready && !currentPrefixError && !uploadError) submit({ kind: 'upload', connectionId, file, prefix: purposePrefix }, ['upload', connectionId, purposePrefix, fileIdentity])
            }}>
              <Box component="label" sx={{ display: 'grid', gap: 0.75, fontSize: 14 }}>
                Document file
                <Box component="input" ref={input} type="file" aria-label="Document file" accept={catalogue.data.supportedExtensions.map(extension => `.${extension}`).join(',')}
                  disabled={!canManage || busy || !ready || !catalogue.data.hostedAvailable} sx={{ width: '100%', minWidth: 0 }} onChange={event => {
                    setFile(event.target.files?.[0] ?? null); setFileIdentity(crypto.randomUUID())
                  }} />
              </Box>
              <Typography variant="caption" color="text.secondary">{catalogue.data.supportedExtensions.map(extension => `.${extension}`).join(', ')} | Up to {bytes(catalogue.data.maxUploadBytes)}</Typography>
              {file && <Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{file.name} ({bytes(file.size)})</Typography>}
              {uploadError && <Alert severity="warning">{uploadError}</Alert>}
              <Button type="submit" variant="outlined" startIcon={<CloudUploadOutlined />} sx={{ alignSelf: 'flex-start' }} disabled={!canManage || busy || !ready || !file || !catalogue.data.hostedAvailable || Boolean(uploadError) || Boolean(currentPrefixError)}>Upload file</Button>
            </Stack>
            <Typography variant="subtitle2" component="h3">Uploaded files</Typography>
            {uploads.isPending && <Typography role="status" variant="body2">Loading uploads...</Typography>}
            {uploads.isError && <Alert severity="error" action={<Button onClick={() => void uploads.refetch()}>Retry</Button>}>{message(uploads.error)}</Alert>}
            {liveSelection && selections.isError && <Alert severity="error" action={<Button onClick={() => void selections.refetch()}>Retry</Button>}>Target indexing status could not be loaded. {message(selections.error)}</Alert>}
            {uploads.isSuccess && !uploads.data.length && <Typography variant="body2" color="text.secondary">No uploaded files.</Typography>}
            <Stack component="ul" spacing={1} sx={{ listStyle: 'none', p: 0, m: 0 }}>
              {(uploads.data ?? []).filter(upload => upload.connectionId === connectionId).map(upload => {
                const receipt = liveSelection ? selections.data?.find(item => item.uploadId === upload.id) : undefined
                const indexingStatus = receipt ? receipt.message ? `${receipt.status}; attention needed`
                  : receipt.status === 'ACTIVE' ? `Active on ${target?.name ?? targetId}` : receipt.status
                  : liveSelection && selections.isError ? 'Indexing status unavailable'
                    : liveSelection && selections.isPending ? 'Checking target indexing'
                      : liveSelection ? 'Uploaded; no indexing receipt' : 'Uploaded; view target indexing status'
                return <Box component="li" key={upload.id} sx={{ borderBottom: 1, borderColor: 'divider', pb: 1, minWidth: 0 }}>
                <FormControlLabel sx={{ m: 0, alignItems: 'flex-start', '& .MuiFormControlLabel-label': { minWidth: 0, overflowWrap: 'anywhere' } }}
                  control={<Checkbox size="small" checked={eligibleIds.includes(upload.id)} disabled={!canManage || busy || !liveSelection || upload.status !== 'UPLOADED' || upload.prefix !== purposePrefix}
                    onChange={event => { setSelectedIds(ids => event.target.checked ? [...ids, upload.id] : ids.filter(id => id !== upload.id)); setPublicApproved(false) }} />}
                  label={<><Typography variant="body2" component="span">{upload.filename}</Typography><Typography variant="caption" component="div" color="text.secondary">
                    {bytes(upload.sizeBytes)} | {upload.prefix} | {upload.status === 'UPLOADED' ? indexingStatus : upload.status}
                  </Typography></>} />
                {receipt?.sourceId && <Typography variant="caption" component="div" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>Source: {receipt.sourceId}{receipt.workId ? ` | Work: ${receipt.workId}` : ''}</Typography>}
                {receipt?.message && <Alert severity="warning">{receipt.message}</Alert>}
                {upload.lastError && <Alert severity="error">{upload.lastError}</Alert>}
              </Box>})}
            </Stack>
            {liveSelection && <>
              {eligibleIds.length > 20 && <Alert severity="warning">Select at most 20 files for one indexing operation.</Alert>}
              <FormControlLabel control={<Checkbox checked={publicApproved} disabled={!canManage || busy || !eligibleIds.length}
                onChange={event => setPublicApproved(event.target.checked)} />} label="I approve the selected files for anonymous public answers." />
              <Button variant="outlined" startIcon={<PlayArrowRounded />} sx={{ alignSelf: 'flex-start' }} disabled={!canManage || busy || !ready || !eligibleIds.length || eligibleIds.length > 20 || Boolean(currentPrefixError)}
                onClick={() => runtime && submit({ kind: 'selection', connectionId, runtime, uploadIds: eligibleIds, publicApproved },
                  ['selection', connectionId, runtime, eligibleIds, publicApproved])}>{selection && !selection.complete ? 'Reconcile selected files' : 'Index selected files'}</Button>
            </>}
            {selection && <Box role="status"><Typography variant="body2">{selection.complete ? 'Selected-file preparation complete. Review target indexing status below.' : 'Selected-file preparation is still in progress.'}</Typography>
              {selection.items.map(item => <Typography key={item.uploadId} variant="body2" sx={{ overflowWrap: 'anywhere' }}>
                {uploads.data?.find(upload => upload.id === item.uploadId)?.filename ?? item.uploadId}: {item.status}{item.workId ? ` | Work: ${item.workId}` : ''}{item.message ? ` | ${item.message}` : ''}
              </Typography>)}
            </Box>}
            <Typography variant="body2" color="text.secondary">Uploads store original files only. Discover, approve, preview and index files against a running target in Document Knowledge.</Typography>
            {indexingHref && <Link component={RouterLink} to={indexingHref}>Open document indexing</Link>}
            <Typography variant="caption" color="text.secondary">Promotion review: staging and production can bind the same bucket and purpose. Review the exact file selection for each target; production needs its own indexing and verification. Vectors are not copied.</Typography>
          </>}
        </>}
        {busy && <Typography variant="body2" role="status">Storage operation in progress...</Typography>}
        {currentCommand && mutation.isError && <Alert severity="error">{message(mutation.error)} Retry the same operation to reuse its operation key.</Alert>}
        {currentCommand && mutation.isSuccess && mutation.variables?.kind === 'upload' && <Alert severity="success">Uploaded. Not indexed by this upload.</Alert>}
      </>}
    </Stack>
  </Box>
}
