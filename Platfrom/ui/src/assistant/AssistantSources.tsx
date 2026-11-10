import { useEffect, useRef, useState } from 'react'
import { Alert, Box, Button, Checkbox, Dialog, DialogActions, DialogContent, DialogTitle, FormControlLabel, IconButton, MenuItem, Stack, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import AttachFileOutlined from '@mui/icons-material/AttachFileOutlined'
import DeleteOutline from '@mui/icons-material/DeleteOutline'
import DescriptionOutlined from '@mui/icons-material/DescriptionOutlined'
import { assistantApi, fileSource } from './api'
import { operation } from './scope'
import type { AssistantController } from './useAssistant'
import type { ReusableSource } from './types'

export function AssistantSources({ controller: c, sourceIds, setSourceIds, operationIds, setOperationIds }: {
  controller: AssistantController; sourceIds: string[]; setSourceIds: (ids: string[]) => void
  operationIds: string[]; setOperationIds: (ids: string[]) => void
}) {
  const [open, setOpen] = useState(false), [mode, setMode] = useState('file'), [title, setTitle] = useState(''), [content, setContent] = useState(''), [url, setUrl] = useState('')
  const [error, setError] = useState(''), [loading, setLoading] = useState(false)
  const input = useRef<HTMLInputElement>(null)
  const [file, setFile] = useState<File | null>(null)
  const [pasteType, setPasteType] = useState('text/plain')
  const [reusable, setReusable] = useState<ReusableSource[]>([]), [reuseIndex, setReuseIndex] = useState(''), [reuseLoading, setReuseLoading] = useState(false)
  const [removing, setRemoving] = useState<{ id: string; title: string } | null>(null)
  const [review, setReview] = useState<{ id: string; title: string } | null>(null), [reviewContent, setReviewContent] = useState(''), [reviewError, setReviewError] = useState(''), [reviewLoading, setReviewLoading] = useState(false)
  useEffect(() => {
    if (!review || !c.session) return
    let current = true
    setReviewContent(''); setReviewError(''); setReviewLoading(true)
    void assistantApi.sourceContent(c.session.id, review.id).then(result => { if (current) setReviewContent(result.content) })
      .catch(error => { if (current) setReviewError(error instanceof Error ? error.message : String(error)) })
      .finally(() => { if (current) setReviewLoading(false) })
    return () => { current = false }
  }, [review?.id, c.session?.id])
  useEffect(() => {
    if (!open || mode !== 'reuse' || !c.session) return
    let current = true
    setReuseLoading(true); setError(''); setReuseIndex('')
    void assistantApi.reusableSources(c.session.id).then(result => { if (current) setReusable(result) })
      .catch(error => { if (current) setError(error instanceof Error ? error.message : String(error)) })
      .finally(() => { if (current) setReuseLoading(false) })
    return () => { current = false }
  }, [open, mode, c.session?.id])
  const toggle = (values: string[], id: string) => values.includes(id) ? values.filter(value => value !== id) : [...values, id]
  const importSource = async () => {
    setLoading(true); setError('')
    try {
      const data = mode === 'file' && file ? await fileSource(file) : mode === 'url'
        ? { title: title.trim() || url, mediaType: 'text/plain', url }
        : { title: title.trim() || 'Pasted documentation', mediaType: pasteType, content }
      const previous = new Set(c.session?.sources.map(source => source.id))
      const selected = reuseIndex === '' ? undefined : reusable[Number(reuseIndex)]
      const result = mode === 'reuse' && selected ? await c.reuseSource(selected.sessionId, selected.sourceId) : await c.addSource(data)
      if (result) {
        const added = result.sources.filter(source => !previous.has(source.id))
        setSourceIds([...new Set([...sourceIds, ...added.map(source => source.id)])])
        setOpen(false); setFile(null); setContent(''); setTitle(''); setUrl('')
        if (added[0]) setReview({ id: added[0].id, title: added[0].title })
      }
    } catch (error) { setError(error instanceof Error ? error.message : String(error)) }
    finally { setLoading(false) }
  }
  return <>
    <Button size="small" startIcon={<AttachFileOutlined />} onClick={() => setOpen(true)} disabled={!c.session || c.busy}>Add source</Button>
    {Boolean(c.session?.sources.length) && <Box component="details" sx={{ fontSize: 12, py: 1, borderBottom: 1, borderColor: 'divider', maxHeight: '28dvh', overflow: 'auto' }}>
      <summary>Sources ({sourceIds.length} selected) / {operationIds.length ? `${operationIds.length} selected operations` : 'all operations in selected sources'}</summary>
      {c.session?.sources.map(source => <Box key={source.id} sx={{ py: 1 }}>
        <Stack direction="row" alignItems="center">
          <FormControlLabel sx={{ flex: 1, minWidth: 0, m: 0, '& .MuiFormControlLabel-label': { fontSize: 12, overflowWrap: 'anywhere' } }} control={<Checkbox size="small" disabled={c.busy} checked={sourceIds.includes(source.id)} onChange={() => {
            setSourceIds(toggle(sourceIds, source.id))
            if (sourceIds.includes(source.id)) { const ids = source.operations.map(operation).filter(Boolean).map(item => item!.id); setOperationIds(operationIds.filter(id => !ids.includes(id))) }
          }} />} label={source.title} />
          <Tooltip title="Review source content"><IconButton aria-label={`Review ${source.title}`} size="small" onClick={() => setReview({ id: source.id, title: source.title })}><DescriptionOutlined fontSize="small" /></IconButton></Tooltip>
          <Tooltip title="Remove source and clear conversation"><span><IconButton aria-label={`Remove ${source.title}`} disabled={c.busy} size="small" onClick={() => setRemoving({ id: source.id, title: source.title })}><DeleteOutline fontSize="small" /></IconButton></span></Tooltip>
        </Stack>
        <Typography variant="caption" color="text.secondary">{source.mediaType} / {source.characters} characters / {source.id}</Typography>
        {source.warnings.map((warning, index) => <Alert key={index} severity="warning" sx={{ mt: 1 }}>{warning}</Alert>)}
        {source.operations.map(operation).filter(Boolean).map(item => <Box key={item!.id} sx={{ pl: 1 }}>
          <FormControlLabel sx={{ m: 0, '& .MuiFormControlLabel-label': { fontSize: 12, overflowWrap: 'anywhere' } }} control={<Checkbox size="small" disabled={c.busy || !sourceIds.includes(source.id)} checked={operationIds.includes(item!.id)} onChange={() => setOperationIds(toggle(operationIds, item!.id))} />} label={item!.label} />
          {item!.pointer && <Typography display="block" variant="caption" color="text.secondary" sx={{ pl: 4 }}>{source.id}{item!.pointer}</Typography>}
        </Box>)}
      </Box>)}
    </Box>}
    <Dialog open={open} onClose={loading || c.pending ? undefined : () => setOpen(false)} fullWidth maxWidth="sm">
      <DialogTitle>Add documentation</DialogTitle>
      <DialogContent><Stack spacing={2}>
        <Alert severity="info">Selected documentation, your request and the scoped configuration are shared with LoomAI's AI provider for this session. Only share material you are authorized to use. Remove credentials and personal or confidential data you do not need to share.</Alert>
        <Tabs value={mode} onChange={(_, value) => setMode(value)} variant="scrollable" scrollButtons="auto" aria-label="Source input"><Tab value="file" label="File" /><Tab value="paste" label="Paste" />{c.status.data?.documentFetchEnabled && <Tab value="url" label="URL" />}<Tab value="reuse" label="Reuse" /></Tabs>
        {['paste', 'url'].includes(mode) && <TextField size="small" label="Source title" value={title} onChange={event => setTitle(event.target.value)} />}
        {mode === 'file' ? <>
          <input ref={input} hidden type="file" accept=".json,.yaml,.yml,.txt,.md,.pdf,application/json,application/yaml,application/pdf,text/plain,text/markdown" onChange={event => { setFile(event.target.files?.[0] ?? null); event.target.value = '' }} />
          <Button variant="outlined" startIcon={<AttachFileOutlined />} onClick={() => input.current?.click()}>Choose file</Button><Typography variant="body2" sx={{ overflowWrap: 'anywhere' }}>{file?.name ?? 'No file selected'}</Typography>
          <Typography variant="caption" color="text.secondary">JSON, YAML, text, Markdown or PDF. Maximum 2 MiB per file.</Typography>
        </> : mode === 'paste' ? <>
          <TextField select size="small" label="Documentation format" value={pasteType} onChange={event => setPasteType(event.target.value)}>
            <MenuItem value="application/json">JSON / OpenAPI / Swagger</MenuItem><MenuItem value="application/yaml">YAML / OpenAPI / Swagger</MenuItem><MenuItem value="text/plain">Prose / Markdown / cURL</MenuItem>
          </TextField>
          <TextField label="Documentation" multiline minRows={7} value={content} onChange={event => setContent(event.target.value)} />
        </> : mode === 'url' ? <TextField label="Public documentation URL" type="url" value={url} onChange={event => setUrl(event.target.value)} /> : <>
          {reuseLoading ? <Typography role="status">Loading reusable sources...</Typography> : !reusable.length ? <Typography variant="body2">No reusable sources are available in this workspace.</Typography> : <TextField select label="Available source" value={reuseIndex} onChange={event => setReuseIndex(event.target.value)}>
            {reusable.map((source, index) => <MenuItem key={`${source.sessionId}:${source.sourceId}`} value={String(index)} sx={{ whiteSpace: 'normal', overflowWrap: 'anywhere' }}>{source.title} / {source.task} / {source.mediaType}</MenuItem>)}
          </TextField>}
        </>}
        {(error || c.error) && <Alert severity="error">{error || c.error}</Alert>}
      </Stack></DialogContent>
      <DialogActions><Button disabled={loading || c.pending} onClick={() => setOpen(false)}>Cancel</Button><Button variant="contained" disabled={loading || c.busy || reuseLoading || (mode === 'file' ? !file : mode === 'paste' ? !content.trim() : mode === 'url' ? !url.trim() : reuseIndex === '')} onClick={() => void importSource()}>{mode === 'reuse' ? 'Reuse source' : 'Share source'}</Button></DialogActions>
    </Dialog>
    <Dialog open={Boolean(review)} onClose={() => setReview(null)} fullWidth maxWidth="md"><DialogTitle>Review {review?.title}</DialogTitle><DialogContent>
      {reviewLoading ? <Typography role="status">Loading imported content...</Typography> : reviewError ? <Alert severity="error">{reviewError}</Alert> : <Box component="pre" sx={{ m: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', fontSize: 12 }}>{reviewContent}</Box>}
    </DialogContent><DialogActions><Button onClick={() => setReview(null)}>Close source review</Button></DialogActions></Dialog>
    <Dialog open={Boolean(removing)} onClose={() => setRemoving(null)} fullWidth maxWidth="xs"><DialogTitle>Remove {removing?.title}?</DialogTitle><DialogContent>Removing this source also clears the assistant conversation; saved draft changes remain.</DialogContent><DialogActions><Button onClick={() => setRemoving(null)}>Keep source</Button><Button color="error" disabled={c.busy} onClick={async () => { if (removing && await c.removeSource(removing.id)) setRemoving(null) }}>Remove source</Button></DialogActions></Dialog>
  </>
}
