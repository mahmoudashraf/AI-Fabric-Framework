import { useEffect, useRef, useState } from 'react'
import { Link as RouterLink, useBlocker, useLocation, useMatch, useNavigate } from 'react-router-dom'
import { useQuery, useQueryClient } from '@tanstack/react-query'
import { Alert, Box, Breadcrumbs, Button, Chip, Dialog, DialogActions, DialogContent, DialogTitle, IconButton,
  Link, Stack, Typography, useTheme } from '@mui/material'
import ArrowBackOutlined from '@mui/icons-material/ArrowBackOutlined'
import ArrowForwardOutlined from '@mui/icons-material/ArrowForwardOutlined'
import AddOutlined from '@mui/icons-material/AddOutlined'
import ExtensionOutlined from '@mui/icons-material/ExtensionOutlined'
import LockOutlined from '@mui/icons-material/LockOutlined'
import CodeOutlined from '@mui/icons-material/CodeOutlined'
import DownloadOutlined from '@mui/icons-material/DownloadOutlined'
import UploadFileOutlined from '@mui/icons-material/UploadFileOutlined'
import DarkModeOutlined from '@mui/icons-material/DarkModeOutlined'
import LightModeOutlined from '@mui/icons-material/LightModeOutlined'
import { useCustomerColorPreference } from '../theme/CustomerConsoleTheme'
import { fetchDeployments } from '../api/platformApi'
import { CreatorFields, NumericDraftContext } from './CreatorFields'
import { datasetList, libraryPath, newDefinition, parseDocument, patchDocument, replaceSourceContract,
  safeBrainReturn, textAt, type SourceKind } from './creatorDocument'
import { pluginLibraryApi as api } from './pluginLibraryApi'
import type { PluginCheck, PluginDetail, PluginSummary } from './types'
import './pluginLibrary.css'

const tasks = [ ['Name & purpose', 'Describe the outcome'], ['Source contract', 'Choose an existing adapter'],
  ['Records & content', 'Map what AI can use'], ['Freshness & access', 'Keep knowledge current'],
  ['Setup inputs', 'Design the connection form'], ['Review & version', 'Save for reuse'] ]
const key = (customerId: string | undefined) => ['customer-plugin-library', customerId ?? 'current']

export function PluginLibraryPage() {
  const location = useLocation(), theme = useTheme(), color = useCustomerColorPreference()
  const edit = useMatch('/library/plugins/:pluginId/edit'), version = useMatch('/library/plugins/:pluginId/versions/:version')
  const isNew = location.pathname === '/library/plugins/new/data'
  const params = new URLSearchParams(location.search), customerId = params.get('customerId') ?? undefined
  const back = safeBrainReturn(location.search)
  const list = useQuery({ queryKey: key(customerId), queryFn: () => api.list(customerId), retry: false })
  const pluginId = edit?.params.pluginId ?? version?.params.pluginId
  const detail = useQuery({ queryKey: [...key(customerId), pluginId], queryFn: () => api.get(pluginId!, customerId), enabled: Boolean(pluginId), retry: false })
  const immutable = useQuery({ queryKey: [...key(customerId), pluginId, 'version', version?.params.version],
    queryFn: () => api.getVersion(pluginId!, version!.params.version!, customerId), enabled: Boolean(version?.params.version), retry: false })
  const error = list.error ?? detail.error ?? immutable.error
  const loading = list.isPending || Boolean(pluginId && detail.isPending) || Boolean(version && immutable.isPending)
  const activeEditor = isNew || Boolean(edit) || Boolean(version)
  return <Box className="plugin-library" data-theme={theme.palette.mode} sx={{
    '--pl-bg': theme.palette.background.default, '--pl-panel': theme.palette.background.paper, '--pl-text': theme.palette.text.primary,
    '--pl-muted': theme.palette.text.secondary, '--pl-border': theme.palette.divider, '--pl-accent': theme.palette.primary.main,
    '--pl-selected': theme.palette.action.selected, '--pl-error': theme.palette.error.main,
  }}>
    <header className="pl-header"><Link href="https://loomai.pro/" color="inherit" underline="none" sx={{ fontSize: 21, fontWeight: 750 }}>LoomAI</Link>
      <Breadcrumbs sx={{ minWidth: 0 }}><Link component={RouterLink} to="/home" color="inherit">Console</Link>
        <Link component={RouterLink} to={libraryPath('/library/plugins', location.search)} color="inherit">My plugins</Link>
        {activeEditor ? <Typography variant="body2">{isNew ? 'New data plugin' : version ? `Version ${version.params.version}` : 'Edit definition'}</Typography> : null}
      </Breadcrumbs><span className="pl-header-space" />
      <IconButton size="small" aria-label={theme.palette.mode === 'dark' ? 'Use light mode' : 'Use dark mode'} onClick={() => color.setPreference(theme.palette.mode === 'dark' ? 'light' : 'dark')}>
        {theme.palette.mode === 'dark' ? <LightModeOutlined fontSize="small" /> : <DarkModeOutlined fontSize="small" />}
      </IconButton>
    </header>
    <aside className="pl-sidebar"><Button component={RouterLink} to={back ?? '/deployments'} startIcon={<ArrowBackOutlined />} sx={{ justifyContent: 'flex-start', mb: 3 }}>{back ? 'Back to Smart Brain' : 'Smart Brains'}</Button>
      <Typography sx={{ fontSize: 18, fontWeight: 650, px: 1, mb: 3 }}>Your plugin library</Typography>
      <Typography variant="caption" color="text.secondary" sx={{ px: 1 }}>WORKSPACE LIBRARY</Typography>
      <Button component={RouterLink} to={libraryPath('/library/plugins', location.search)} startIcon={<ExtensionOutlined />} sx={{ mt: 1, justifyContent: 'flex-start', bgcolor: 'action.selected' }}>My plugins</Button>
      <Stack direction="row" alignItems="center" gap={1} sx={{ px: 1, mt: 2 }}><LockOutlined sx={{ fontSize: 14 }} /><Typography variant="caption" color="text.secondary">Private to your workspace</Typography></Stack>
    </aside>
    <main className="pl-main">
      {error ? <Alert severity="error" action={<Button onClick={() => { void list.refetch(); if (pluginId) void detail.refetch(); if (version) void immutable.refetch() }}>Retry</Button>}>{error.message}</Alert>
        : loading ? <Typography color="text.secondary" role="status">Loading your plugin library...</Typography>
        : activeEditor ? <DefinitionEditor key={`${pluginId ?? 'new'}:${version?.params.version ?? 'draft'}:${customerId ?? 'current'}`}
          detail={detail.data} immutableSource={immutable.data?.manifestJson} customerId={customerId} canManage={Boolean(list.data?.canManage)} />
        : list.data ? <Library plugins={list.data.plugins} canManage={list.data.canManage} customerId={customerId} /> : null}
    </main>
  </Box>
}

function Library({ plugins, canManage, customerId }: { plugins: PluginSummary[]; canManage: boolean; customerId?: string }) {
  const location = useLocation(), [search, setSearch] = useState(''), [selectedId, setSelectedId] = useState(''), [showArchived, setShowArchived] = useState(false)
  const [chooseBrain, setChooseBrain] = useState(false), [brains, setBrains] = useState<{ id: string; name: string }[]>([]), [brainError, setBrainError] = useState('')
  const selected = useQuery({ queryKey: [...key(customerId), selectedId], queryFn: () => api.get(selectedId, customerId), enabled: Boolean(selectedId), retry: false })
  const current = selected.data
  const filtered = plugins.filter(plugin => (showArchived || plugin.status !== 'ARCHIVED') && `${plugin.displayName} ${plugin.description ?? ''}`.toLowerCase().includes(search.trim().toLowerCase()))
  const useHref = (brain: string, version?: string) => {
    const back = safeBrainReturn(location.search)
    const url = new URL(back ?? `/smart-brains/${encodeURIComponent(brain)}/capabilities/add`, 'https://console.invalid')
    url.pathname = `/smart-brains/${encodeURIComponent(brain)}/capabilities/add`
    url.searchParams.set('pluginId', selectedId); url.searchParams.set('pluginVersion', version ?? current?.latestVersion ?? '')
    url.searchParams.set('view', 'setup'); url.searchParams.set('type', 'DATA')
    return `${url.pathname}${url.search}`
  }
  const back = safeBrainReturn(location.search), backBrain = back?.split('/')[2]
  const loadBrains = async () => {
    setChooseBrain(true); setBrainError('')
    try {
      const items = await fetchDeployments()
      setBrains(items.filter(item => item.binding?.customerId === current?.customerId).map(({ id, name }) => ({ id, name })))
    } catch (error) { setBrainError(error instanceof Error ? error.message : 'Unable to load Smart Brains.') }
  }
  return <>
    <div className="pl-heading"><div><h1>My plugins</h1><p>Reusable integrations owned by your workspace.</p></div>
      {canManage ? <Button component={RouterLink} to={libraryPath('/library/plugins/new/data', location.search)} variant="contained" startIcon={<AddOutlined />}>Create a data plugin</Button> : null}
    </div>
    <div className="pl-library-filter"><input aria-label="Find my plugins" placeholder="Find a plugin" value={search} onChange={event => setSearch(event.target.value)} />
      <label className="pl-checkbox"><input type="checkbox" checked={showArchived} onChange={event => setShowArchived(event.target.checked)} />Show archived</label></div>
    <div className={`pl-library-content${selectedId ? ' pl-with-detail' : ''}`}>
      <section className="pl-plugin-list" aria-label="My plugin definitions">
        {filtered.map(plugin => <button type="button" className={`pl-plugin-row${selectedId === plugin.pluginId ? ' pl-selected' : ''}`} key={plugin.pluginId} onClick={() => setSelectedId(plugin.pluginId)}>
          <span className="pl-plugin-icon"><ExtensionOutlined /></span><span className="pl-row-copy"><strong>{plugin.displayName || 'Untitled data plugin'}</strong><span>{plugin.description}</span><small>Data / {plugin.sourceKind || 'Source contract'} / {plugin.savedInstallations ?? 0} saved installations</small></span>
          <Chip size="small" label={plugin.status === 'ARCHIVED' ? 'Archived' : plugin.latestVersion ? `v${plugin.latestVersion}` : 'Draft'} variant="outlined" />
        </button>)}
        {!filtered.length ? <div className="pl-empty"><h2>{search ? 'No matching plugins' : showArchived ? 'No plugins yet' : 'No active plugins yet'}</h2>
          {search ? <Button onClick={() => setSearch('')}>Clear search</Button> : canManage ? <Button component={RouterLink} to={libraryPath('/library/plugins/new/data', location.search)}>Create a data plugin</Button> : null}</div> : null}
      </section>
      {selectedId ? <section className="pl-library-detail" aria-label="Selected plugin">
        <Button size="small" onClick={() => setSelectedId('')} sx={{ alignSelf: 'flex-start' }}>Close details</Button>
        {selected.isPending ? <p>Loading definition...</p> : selected.error ? <Alert severity="error">{selected.error.message}</Alert> : current ? <>
          <div className="pl-eyebrow">Selected plugin <LockOutlined fontSize="inherit" /></div><h2>{current.displayName || 'Untitled data plugin'}</h2><p>{current.description}</p>
          <dl className="pl-summary"><dt>Usable version</dt><dd>{current.latestVersion ?? 'Not created'}</dd><dt>Draft revision</dt><dd>{current.draftRevision}</dd><dt>Saved installations</dt><dd>{current.savedInstallations ?? 0}</dd><dt>Recorded applied uses</dt><dd>{current.appliedUses ?? 0}</dd></dl>
          <Stack direction="row" gap={1} flexWrap="wrap"><Button component={RouterLink} to={libraryPath(`/library/plugins/${encodeURIComponent(current.pluginId)}/edit`, location.search)} startIcon={<CodeOutlined />}>{current.canManage && current.status !== 'ARCHIVED' ? 'Edit definition' : 'View definition'}</Button>
            {current.latestVersion && current.status !== 'ARCHIVED' ? backBrain ? <Button variant="contained" component={RouterLink} to={useHref(backBrain)} startIcon={<AddOutlined />}>Use in this Brain</Button>
              : <Button variant="contained" onClick={() => void loadBrains()} startIcon={<AddOutlined />}>Use in a Brain</Button> : null}</Stack>
          <h3>Reusable versions</h3>{current.versions.map(item => <Link key={item.version} component={RouterLink} to={libraryPath(`/library/plugins/${encodeURIComponent(current.pluginId)}/versions/${encodeURIComponent(item.version)}`, location.search)}>Version {item.version}</Link>)}
          <h3>Installations &amp; applied releases</h3>{current.usage.length ? current.usage.map((item, index) => <Link key={`${item.deploymentId}:${index}`} component={RouterLink} to={item.returnTo ?? `/smart-brains/${encodeURIComponent(item.deploymentId)}/capabilities`} color="inherit" sx={{ py: 1, borderTop: 1, borderColor: 'divider', fontSize: 13 }}>{item.brainName ?? item.deploymentId} / {item.version} / {item.state}</Link>) : <p>Not connected to a Smart Brain yet.</p>}
          <small>Version edits do not update installed Brains automatically.</small>
        </> : null}
      </section> : null}
    </div>
    <footer className="pl-footer"><span>Private source definitions. Your business data stays in its source system.</span></footer>
    <Dialog open={chooseBrain} onClose={() => setChooseBrain(false)} fullWidth maxWidth="sm"><DialogTitle>Choose a Smart Brain</DialogTitle><DialogContent>
      {brainError ? <Alert severity="error">{brainError}</Alert> : brains.length ? <Stack>{brains.map(brain => <Button key={brain.id} component={RouterLink} to={useHref(brain.id)} sx={{ justifyContent: 'flex-start' }}>{brain.name}</Button>)}</Stack> : <Typography color="text.secondary">No Smart Brains for this workspace are available yet.</Typography>}
    </DialogContent><DialogActions><Button onClick={() => setChooseBrain(false)}>Cancel</Button></DialogActions></Dialog>
  </>
}

function DefinitionEditor({ detail, immutableSource, customerId, canManage }: { detail?: PluginDetail; immutableSource?: string; customerId?: string; canManage: boolean }) {
  const location = useLocation(), navigate = useNavigate(), client = useQueryClient()
  const [saved, setSaved] = useState(detail)
  const [source, setSource] = useState(() => immutableSource ?? detail?.manifestJson ?? newDefinition(`source-${crypto.randomUUID().slice(0, 8)}`))
  const [baseline, setBaseline] = useState(source), [step, setStep] = useState(0), [dataset, setDataset] = useState(0), [showSource, setShowSource] = useState(false)
  const feedback = location.state?.pluginFeedback as { error?: string; message?: string; check?: PluginCheck } | undefined
  const [busy, setBusy] = useState(false), [error, setError] = useState(feedback?.error ?? ''), [message, setMessage] = useState(feedback?.message ?? ''), [check, setCheck] = useState<PluginCheck | null>(feedback?.check ?? null)
  const [archiveOpen, setArchiveOpen] = useState(false), [sourceChange, setSourceChange] = useState<SourceKind | null>(null)
  const [reloadOpen, setReloadOpen] = useState(false)
  const [numericDrafts, setNumericDrafts] = useState<Record<string, string>>({})
  const invalidFields = Object.keys(numericDrafts).length > 0
  const clearNumeric = () => setNumericDrafts({})
  const leaving = useRef(false), fileInput = useRef<HTMLInputElement>(null)
  const readOnly = !canManage || saved?.canManage === false || saved?.status === 'ARCHIVED' || Boolean(immutableSource)
  const parsed = parseDocument(source), document = parsed.document
  const dirty = !readOnly && (source !== baseline || invalidFields)
  const blocker = useBlocker(() => !leaving.current && (busy || dirty))
  useEffect(() => {
    if (detail || !saved || busy) return
    leaving.current = true
    navigate(libraryPath(`/library/plugins/${encodeURIComponent(saved.pluginId)}/edit`, location.search), {
      replace: true, state: { pluginFeedback: { error, message, check } },
    })
  }, [saved, busy, detail, error, message, check, navigate, location.search])
  useEffect(() => {
    if (!busy && !dirty) return
    const prevent = (event: BeforeUnloadEvent) => { event.preventDefault(); event.returnValue = '' }
    window.addEventListener('beforeunload', prevent); return () => window.removeEventListener('beforeunload', prevent)
  }, [busy, dirty])
  const resetFeedback = () => { setMessage(''); setError(''); setCheck(null) }
  const edit = (edits: Parameters<typeof patchDocument>[1]) => {
    try { setSource(current => patchDocument(current, edits)); resetFeedback() } catch (error) { setError(error instanceof Error ? error.message : 'Unable to edit this field.') }
  }
  const adopt = (result: PluginDetail) => { setSaved(result); setBaseline(result.manifestJson); setSource(result.manifestJson); clearNumeric(); client.setQueryData([...key(customerId), result.pluginId], result) }
  const command = async (action: 'save' | 'check' | 'version' | 'archive') => {
    if (readOnly || busy || !document || invalidFields) return
    setBusy(true); setError(''); setMessage('')
    let current = saved
    try {
      if (!current) { current = await api.create({ manifestJson: source }, customerId); adopt(current) }
      if (action === 'check') {
        const result = await api.check(current.pluginId, { manifestJson: source, expectedRevision: current.draftRevision }, customerId)
        setCheck(result); setMessage(result.valid ? 'Configuration checked. Provider connectivity and indexing have not been tested.' : 'Review the configuration issues below.')
      } else if (action === 'version') {
        const result = await api.createVersion(current.pluginId, { manifestJson: source, expectedRevision: current.draftRevision, version: textAt(document, ['version']) }, customerId)
        current = result; adopt(result); setCheck(null); setMessage('Reusable version saved. No Brain or live environment was changed.')
      } else if (action === 'archive') {
        const result = await api.archive(current.pluginId, { expectedRevision: current.draftRevision }, customerId)
        current = result; adopt(result); setArchiveOpen(false); setMessage('Definition archived. Existing pinned versions are retained.')
      } else if (saved) {
        const result = await api.save(current.pluginId, { manifestJson: source, expectedRevision: current.draftRevision }, customerId)
        current = result; adopt(result); setCheck(null); setMessage('Private draft saved. No Brain was changed.')
      } else setMessage('Private draft saved. No Brain was changed.')
      await client.invalidateQueries({ queryKey: key(customerId) })
    } catch (error) { setError(error instanceof Error ? error.message : 'The operation failed. Your edits are retained.') }
    finally { setBusy(false) }
  }
  const sourceDownload = () => {
    const url = URL.createObjectURL(new Blob([source], { type: 'application/json' }))
    const anchor = window.document.createElement('a'); anchor.href = url; anchor.download = 'data-plugin.json'; anchor.click(); URL.revokeObjectURL(url)
  }
  const name = document ? textAt(document, ['displayName']) : ''
  const description = document ? textAt(document, ['description']) : ''
  const versionHref = saved?.latestVersion ? libraryPath(`/library/plugins/${encodeURIComponent(saved.pluginId)}/versions/${encodeURIComponent(saved.latestVersion)}`, location.search) : null
  const back = safeBrainReturn(location.search)
  const chosenVersion = immutableSource && document ? textAt(document, ['version']) : saved?.latestVersion
  const useHref = saved && saved.status !== 'ARCHIVED' && chosenVersion && back ? (() => { const url = new URL(back, 'https://console.invalid'); const brainId = url.pathname.split('/')[2]; url.pathname = `/smart-brains/${brainId}/capabilities/add`; url.searchParams.set('pluginId', saved.pluginId); url.searchParams.set('pluginVersion', chosenVersion); url.searchParams.set('view', 'setup'); url.searchParams.set('type', 'DATA'); return `${url.pathname}${url.search}` })() : null
  return <>
    <div className="pl-heading"><div><h1>{immutableSource ? `${name || 'Data plugin'} / immutable version` : 'Teach your AI from a new source'}</h1><p>{readOnly ? 'Read-only reusable definition.' : 'Create a reusable Data plugin, private to your workspace.'}</p></div>
      <Button variant="outlined" startIcon={<CodeOutlined />} onClick={() => setShowSource(!showSource)}>{showSource ? 'Guided form' : 'Source'}</Button></div>
    <div className="pl-editor-content">
      <nav className="pl-task-rail" aria-label="Data plugin creation steps">{tasks.map(([label, help], index) => <button key={label} className={step === index ? 'pl-selected' : ''} type="button" onClick={() => setStep(index)} disabled={busy} aria-current={step === index ? 'step' : undefined}>
        <span className="pl-step-number">{index + 1}</span><span><strong>{label}</strong><small>{help}</small></span></button>)}</nav>
      <section className="pl-editor-pane" aria-label={showSource ? 'Definition source' : tasks[step][0]}>
        {error ? <Alert severity="error" action={saved ? <Button disabled={busy} onClick={() => setReloadOpen(true)}>Reload saved draft</Button> : undefined}>{error}</Alert> : null}{message ? <Alert severity={check && !check.valid ? 'warning' : 'success'}>{message}</Alert> : null}
        {showSource ? <>
          <Stack direction="row" gap={1} flexWrap="wrap"><Button size="small" startIcon={<UploadFileOutlined />} disabled={readOnly || busy} onClick={() => fileInput.current?.click()}>Import JSON</Button><Button size="small" startIcon={<DownloadOutlined />} onClick={sourceDownload}>Export JSON</Button></Stack>
          <input type="file" ref={fileInput} accept="application/json,.json" hidden onChange={async event => { const input = event.target; const file = input.files?.[0]; if (!file) return; if (file.size > 2 * 1024 * 1024) { setError('Definition imports are limited to 2 MB.'); return }; setSource(await file.text()); clearNumeric(); resetFeedback(); input.value = '' }} />
          <label className="pl-field pl-source-field"><span>Complete plugin manifest</span><textarea aria-label="Complete plugin manifest" spellCheck={false} value={source} disabled={readOnly || busy} onChange={event => { setSource(event.target.value); clearNumeric(); resetFeedback() }} /></label>
          {parsed.error ? <Alert severity="error">{parsed.error} Your source has not been replaced.</Alert> : null}
        </> : document ? <>
          <h2>{tasks[step][0]}</h2>
          {datasetList(document).length > 1 ? <label className="pl-field"><span>Dataset</span><select value={dataset} onChange={event => setDataset(Number(event.target.value))}>{datasetList(document).map((_, index) => <option key={index} value={index}>{textAt(document, ['contributions', 'datasets', index, 'datasetId']) || `Dataset ${index + 1}`}</option>)}</select></label> : null}
          <NumericDraftContext.Provider value={{ drafts: numericDrafts, setDraft: (path, value) => setNumericDrafts(current => { const next = { ...current }; if (value === undefined) delete next[path]; else next[path] = value; return next }) }}>
            <CreatorFields document={document} step={step} dataset={dataset} edit={edit} disabled={readOnly || busy} immutableId={Boolean(saved?.versions.length)} issues={check?.issues ?? []} onSourceChange={setSourceChange} />
          </NumericDraftContext.Provider>
          {invalidFields ? <Alert severity="warning">Finish incomplete numeric values before saving or checking. Your typed values are retained in their steps.</Alert> : null}
          {step === 5 ? <>
            {!readOnly ? <Button onClick={() => void command('check')} disabled={busy || invalidFields} startIcon={<CodeOutlined />} sx={{ alignSelf: 'flex-start' }}>{saved ? 'Check configuration' : 'Save draft & check'}</Button> : null}
            {check?.issues.map((issue, index) => <Alert key={`${issue.path}:${index}`} severity="warning"><strong>{issue.path}</strong>: {issue.message}</Alert>)}
            {useHref && !dirty ? <Button component={RouterLink} to={useHref} variant="outlined" startIcon={<AddOutlined />} sx={{ alignSelf: 'flex-start' }}>Use in this Brain</Button> : versionHref && !dirty ? <Button component={RouterLink} to={versionHref}>View reusable version</Button> : null}
          </> : null}
        </> : <Alert severity="error">{parsed.error} Open Source to correct it. No defaults have replaced your edits.</Alert>}
      </section>
      <aside className="pl-outcome" aria-label="What this enables"><div className="pl-eyebrow">What this enables</div><span className="pl-plugin-icon"><ExtensionOutlined /></span>
        <h3>{name || 'Your new source'}</h3><p>{description || 'Name the source and describe its purpose.'}</p><Chip icon={<LockOutlined />} size="small" label="Private to your workspace" variant="outlined" />
        <div className="pl-flow">Your source <ArrowForwardOutlined fontSize="small" /> AI knowledge</div><p className="pl-caption">Each installation supplies its authorized connection. No credentials are embedded here.</p>
      </aside>
    </div>
    <footer className="pl-footer"><span>{readOnly ? 'Read-only definition' : dirty ? 'Unsaved definition changes' : saved ? `Saved private draft / revision ${saved.draftRevision}` : 'New private definition'}{busy ? ' / Working...' : ''}</span>
      <div className="pl-footer-actions">
        {saved && !readOnly ? <Button color="warning" disabled={busy || dirty} onClick={() => setArchiveOpen(true)}>Archive</Button> : null}
        {!readOnly ? <Button disabled={busy || !document || invalidFields} onClick={() => void command('save')}>Save draft</Button> : null}
        <Button disabled={busy || step === 0} startIcon={<ArrowBackOutlined />} onClick={() => setStep(step - 1)}>Back</Button>
        {step < 5 ? <Button variant="contained" disabled={busy} endIcon={<ArrowForwardOutlined />} onClick={() => setStep(step + 1)}>Continue</Button> : !readOnly ? <Button variant="contained" disabled={busy || !document || invalidFields} onClick={() => void command('version')}>Save reusable version</Button> : null}
      </div></footer>
    <Dialog open={sourceChange !== null} onClose={() => setSourceChange(null)}><DialogTitle>Change the source contract?</DialogTitle><DialogContent>The selected dataset's connector settings will be replaced. Other definition fields stay unchanged. Review setup inputs before saving a reusable version.</DialogContent><DialogActions><Button onClick={() => setSourceChange(null)}>Cancel</Button><Button onClick={() => { try { setSource(replaceSourceContract(source, dataset, sourceChange!)); clearNumeric(); resetFeedback(); setSourceChange(null) } catch (error) { setError(error instanceof Error ? error.message : 'Unable to change source.') } }}>Change source</Button></DialogActions></Dialog>
    <Dialog open={archiveOpen} onClose={() => { if (!busy) setArchiveOpen(false) }}><DialogTitle>Archive this definition?</DialogTitle><DialogContent>It will no longer be offered for new installations. Existing pinned versions and live Brains are retained.</DialogContent><DialogActions><Button disabled={busy} onClick={() => setArchiveOpen(false)}>Cancel</Button><Button disabled={busy} color="warning" onClick={() => void command('archive')}>Archive definition</Button></DialogActions></Dialog>
    <Dialog open={reloadOpen} onClose={() => { if (!busy) setReloadOpen(false) }}><DialogTitle>Load the current saved draft?</DialogTitle><DialogContent>This discards your local edits. Export the source first to keep a copy. No installed Brain will change.</DialogContent><DialogActions><Button disabled={busy} onClick={() => setReloadOpen(false)}>Keep editing</Button><Button color="warning" disabled={busy} onClick={async () => {
      if (!saved) return
      setBusy(true)
      try { adopt(await api.get(saved.pluginId, customerId)); resetFeedback(); setReloadOpen(false) }
      catch (error) { setError(error instanceof Error ? error.message : 'The current draft could not be loaded.') }
      finally { setBusy(false) }
    }}>Discard &amp; reload</Button></DialogActions></Dialog>
    <Dialog open={blocker.state === 'blocked'}><DialogTitle>{busy ? 'An operation is still running' : 'Leave this definition?'}</DialogTitle><DialogContent>{busy ? 'Wait for its result before leaving.' : 'Your unsaved edits will be discarded. Saved versions and installed Brains are unchanged.'}</DialogContent><DialogActions><Button onClick={() => blocker.state === 'blocked' && blocker.reset()}>Keep editing</Button><Button color="warning" disabled={busy} onClick={() => { leaving.current = true; if (blocker.state === 'blocked') blocker.proceed() }}>Discard &amp; leave</Button></DialogActions></Dialog>
  </>
}
