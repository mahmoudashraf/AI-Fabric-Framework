import { useEffect, useMemo, useRef, useState } from 'react'
import { Alert, Box, Button, Checkbox, Chip, CircularProgress, FormControlLabel, IconButton, MenuItem, Stack, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import AutoAwesomeOutlined from '@mui/icons-material/AutoAwesomeOutlined'
import PreviewOutlined from '@mui/icons-material/PreviewOutlined'
import PlayArrowRounded from '@mui/icons-material/PlayArrowRounded'
import SaveOutlined from '@mui/icons-material/SaveOutlined'
import AddRounded from '@mui/icons-material/AddRounded'
import DeleteOutlineRounded from '@mui/icons-material/DeleteOutlineRounded'
import StopRounded from '@mui/icons-material/StopRounded'
import FactCheckOutlined from '@mui/icons-material/FactCheckOutlined'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { fetchDocumentPreparation, saveDocumentPreparation, fetchDocumentEvaluations, startDocumentEvaluation, cancelDocumentEvaluation,
  previewDocumentSource, PlatformApiError, type DeploymentTargetReference, type DocumentPreparationConfig, type DocumentPreparationSnapshot,
  type DocumentPreviewResult, type DocumentEvaluationRun } from '../../api/platformApi'
import { useAssistantSource } from '../../assistant/AssistantWorkspace'
import { assistantApi } from '../../assistant/api'
import type { BrainEditorState } from '../../brain/BrainWorkspaceContext'

type Props = {
  target: DeploymentTargetReference; sourceId: string; canEdit: boolean
  preview?: DocumentPreviewResult; previewBusy: boolean; previewError?: string
  onPreview: () => void; onIndex: () => void; indexBusy: boolean
  onEditorState: (state: BrainEditorState) => void
}
const message = (error: unknown) => error instanceof Error ? error.message : String(error)
const running = (run: DocumentEvaluationRun) => ['QUEUED', 'RUNNING'].includes(run.status)
const metricLabel: Record<string, string> = { faithfulness: 'Supported by excerpts', relevance: 'Answers the question', referenceAgreement: 'Agrees with reference' }

export function DocumentPreparationPanel({ target, sourceId, canEdit: requestedEdit, preview, previewBusy, previewError, onPreview, onIndex, indexBusy, onEditorState }: Props) {
  const canEdit = requestedEdit && !indexBusy
  const client = useQueryClient(), assistant = useAssistantSource()
  const key = ['document-preparation', target.deploymentId, target.targetProfileId, target.releaseId, sourceId]
  const evaluationKey = ['document-evaluations', target.deploymentId, target.targetProfileId, target.releaseId, sourceId]
  const preparation = useQuery({ queryKey: key, queryFn: () => fetchDocumentPreparation(target, sourceId), retry: false, refetchInterval: indexBusy ? 1500 : false })
  useEffect(() => { if (!indexBusy) void preparation.refetch() }, [indexBusy])
  const evaluations = useQuery({ queryKey: evaluationKey, queryFn: () => fetchDocumentEvaluations(target, sourceId), enabled: preparation.isSuccess,
    retry: false, refetchInterval: query => query.state.data?.some(running) ? 1500 : false })
  const [draft, setDraft] = useState<{ revision: number; value: DocumentPreparationConfig } | null>(null)
  const [view, setView] = useState(0), [advanced, setAdvanced] = useState(false), [selectedRun, setSelectedRun] = useState('')
  const evaluationOperation = useRef(crypto.randomUUID())
  const snapshot = preparation.data
  const config = draft?.value ?? snapshot?.configuration
  const dirty = Boolean(draft && snapshot && JSON.stringify(draft.value) !== JSON.stringify(snapshot.configuration))
  const stale = Boolean(draft && snapshot && draft.revision !== snapshot.revision)
  const runs = evaluations.data ?? [], activeRun = runs.find(running)
  const run = runs.find(item => item.id === selectedRun) ?? runs[0]
  const previous = useMemo(() => run ? runs.find(item => item.id !== run.id && item.createdAt < run.createdAt && item.status === 'COMPLETED') : undefined, [runs, run])
  const save = useMutation({ mutationFn: () => saveDocumentPreparation(target, sourceId, draft?.revision ?? snapshot!.revision, config!),
    onSuccess: async (saved: DocumentPreparationSnapshot) => {
      client.setQueryData(key, saved); setDraft(null)
      await client.invalidateQueries({ queryKey: ['document-sources', target.deploymentId, target.targetProfileId, target.releaseId] })
    } })
  const evaluate = useMutation({ mutationFn: () => startDocumentEvaluation(target, sourceId, snapshot!.revision, evaluationOperation.current), onSuccess: async result => {
    evaluationOperation.current = crypto.randomUUID()
    setSelectedRun(result.id); await client.invalidateQueries({ queryKey: evaluationKey })
  } })
  const cancel = useMutation({ mutationFn: (id: string) => cancelDocumentEvaluation(target, sourceId, id), onSuccess: async () => { await client.invalidateQueries({ queryKey: evaluationKey }) } })
  const latestSave = useRef(() => save.mutateAsync()); latestSave.current = () => save.mutateAsync()
  useEffect(() => {
    onEditorState({ dirty, busy: save.isPending || evaluate.isPending || cancel.isPending, canSave: canEdit && dirty && !stale,
      save: () => latestSave.current(), discard: () => setDraft(null), primaryLabel: 'Save document settings',
      message: dirty ? 'Save or discard document settings before changing context.' : 'Document settings are separate from the active index. Reindex explicitly after changing extraction or chunks.' })
    return () => onEditorState({ dirty: false, busy: false })
  }, [dirty, stale, canEdit, save.isPending, evaluate.isPending, cancel.isPending, onEditorState])
  const edit = (update: (current: DocumentPreparationConfig) => DocumentPreparationConfig) => {
    if (config && snapshot) setDraft({ revision: draft?.revision ?? snapshot.revision, value: update(config) })
  }
  const optimize = (evaluateFirst = false) => {
    if (!assistant || !snapshot || dirty) return
    let cancelled = false, evaluationId: string | null = null
    const operationKey = crypto.randomUUID()
    assistant({ scope: { resourceKind: 'DOCUMENT_SOURCE', resourceId: [target.deploymentId, target.targetProfileId, target.releaseId, sourceId].join('|'),
      task: 'DOCUMENT_PREPARATION', editorContext: { section: 'DOCUMENT_PREPARATION', selectedDefinitionIds: [sourceId] } }, prepare: true,
      notice: evaluateFirst ? `Run up to ${Math.min(config!.questions.length, config!.evaluationOptions.maxQuestions)} saved test questions, then let AI prepare improvements from the real results. This uses your AI allowance and never reindexes automatically.`
        : 'AI will inspect a bounded sample of this document and the selected evaluation. It can save candidate settings and questions; it cannot index or publish.',
      cancel: evaluateFirst ? async () => { cancelled = true; if (evaluationId) await cancelDocumentEvaluation(target, sourceId, evaluationId) } : undefined,
      load: async id => {
        let report = run
        if (evaluateFirst) {
          report = await startDocumentEvaluation(target, sourceId, snapshot.revision, operationKey); evaluationId = report.id
          if (cancelled) { await cancelDocumentEvaluation(target, sourceId, report.id); throw new Error('Evaluation cancelled. No optimization request was sent.') }
          const deadline = Date.now() + 135000
          while (running(report) && !cancelled && Date.now() < deadline) {
            await new Promise(resolve => setTimeout(resolve, 1200))
            const current = await fetchDocumentEvaluations(target, sourceId)
            const observed = current.find(item => item.id === evaluationId)
            if (!observed) throw new Error('The evaluation receipt is unavailable. Open Evaluate to inspect its status.')
            report = observed
          }
          await client.invalidateQueries({ queryKey: evaluationKey })
          if (cancelled) throw new Error('Evaluation cancelled. No optimization request was sent.')
          if (running(report)) { await cancelDocumentEvaluation(target, sourceId, report.id); throw new Error('Evaluation deadline reached. Inspect the retained partial results.') }
          if (!['COMPLETED', 'PARTIAL'].includes(report.status)) throw new Error(`Evaluation ${report.status.toLowerCase()}. Inspect the retained results before optimizing.`)
        }
        const sample = await previewDocumentSource(target, sourceId)
        if (cancelled) throw new Error('Optimization cancelled.')
        const data = { sourceId, sourceVersion: sample.source.candidateVersion, sampleIsPartial: sample.chunksTruncated || sample.chunks.some(chunk => chunk.truncated),
          extraction: sample.extraction, documentCount: sample.documentCount, chunkCount: sample.chunkCount,
          excerpts: sample.chunks.map(chunk => ({ chunkId: chunk.chunkId, text: chunk.contentPreview, truncated: chunk.truncated })),
          evaluation: report ? { id: report.id, sourceVersion: report.sourceVersion, status: report.status, configuration: report.configuration,
            cases: report.cases.map(item => ({ question: item.question, answer: item.answer?.slice(0, 2500), referenceReviewed: item.referenceReviewed,
              referenceAnswer: item.referenceAnswer, evaluation: item.evaluation, status: item.status })) } : null }
        let content = JSON.stringify(data)
        if (content.length > 31000) content = JSON.stringify({ ...data, evaluation: report ? { id: report.id, status: report.status, sourceVersion: report.sourceVersion,
          cases: report.cases.slice(0, 3).map(item => ({ question: item.question, evaluation: item.evaluation, status: item.status })) } : null })
        if (content.length > 32000) throw new Error('Selected context is too large. Select a smaller evaluation or document sample.')
        return assistantApi.source(id, { title: `Document sample and quality evidence: ${sourceId}`, mediaType: 'text/plain', content })
      } })
  }
  if (preparation.isLoading) return <CircularProgress size={22} aria-label="Loading document settings" />
  if (!config || !snapshot) return <Stack spacing={1}><Alert severity={preparation.error instanceof PlatformApiError && preparation.error.status === 404 ? 'info' : 'error'}>Document settings could not be loaded. A compatible runtime is required. Existing preview and indexing remain available.{preparation.error ? ` ${message(preparation.error)}` : ''}</Alert>
    <Stack direction="row" spacing={1}><Button startIcon={<PreviewOutlined />} onClick={onPreview}>Preview</Button><Button startIcon={<PlayArrowRounded />} disabled={!canEdit || indexBusy} onClick={onIndex}>Index</Button></Stack></Stack>
  const number = (label: string, value: number, min: number, max: number, change: (value: number) => void, help: string, step = 1) =>
    <TextField key={label} size="small" type="number" label={label} value={value} disabled={!canEdit} onChange={event => change(Number(event.target.value))}
      inputProps={{ min, max, step }} helperText={help} sx={{ minWidth: 160, flex: '1 1 180px' }} />
  const currentPreview = preview?.source.candidateVersion === snapshot.sourceVersion ? preview : undefined
  return <Box sx={{ borderTop: 1, borderColor: 'divider', mt: 1 }}>
    <Stack direction={{ xs: 'column', md: 'row' }} alignItems={{ md: 'center' }} justifyContent="space-between" spacing={1} sx={{ py: 2 }}>
      <Box><Typography variant="h6">Make this document useful to your AI</Typography><Typography variant="body2" color="text.secondary">Prepare the text, index a reviewed candidate, then test real questions.</Typography></Box>
      <Button variant="contained" startIcon={<AutoAwesomeOutlined />} disabled={!assistant || !canEdit || dirty || indexBusy} onClick={() => optimize()}>Optimize with AI</Button>
    </Stack>
    <Tabs value={view} onChange={(_, value: number) => setView(value)} variant="scrollable" aria-label="Document journey">
      <Tab label="1. Prepare" /><Tab label="2. Index" /><Tab label="3. Evaluate" />
    </Tabs>
    {stale && <Alert severity="warning" sx={{ mt: 2 }}>Settings changed elsewhere. Discard your buffer and reload before saving.</Alert>}
    {save.isError && <Alert severity="error">{message(save.error)}</Alert>}
    {save.isSuccess && !dirty && save.data.revision === snapshot.revision && view === 0 && <Alert severity="success" sx={{ mt: 1 }}>Settings saved. The active index was not changed.</Alert>}
    {view === 0 && <Stack spacing={2.5} sx={{ py: 2 }}>
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap><Chip size="small" label={`${config.processing.chunkTokens} tokens per chunk`} /><Chip size="small" label={`${config.processing.overlapTokens} token overlap`} />
        <Chip size="small" label={`OCR: ${config.processing.ocrMode.toLowerCase()}`} /><Chip size="small" variant="outlined" label={snapshot.ocrAvailable ? 'Local OCR available' : 'OCR not installed'} /></Stack>
      <Typography variant="body2">Keep related facts together. Overlap preserves context at chunk boundaries, with more embedding storage. OCR reads scanned pages and images; Word and text-based PDFs use their text directly.</Typography>
      <Stack direction="row" spacing={1}><Button startIcon={<PreviewOutlined />} disabled={dirty || previewBusy} onClick={onPreview}>{previewBusy ? 'Reading document...' : 'Inspect extracted chunks'}</Button>
        <Button startIcon={<SaveOutlined />} disabled={!dirty || stale || !canEdit || save.isPending} onClick={() => save.mutate()}>Save settings</Button>
        {dirty && <Button onClick={() => setDraft(null)}>Discard</Button>}</Stack>
      <Button sx={{ alignSelf: 'flex-start' }} onClick={() => setAdvanced(!advanced)}>{advanced ? 'Hide advanced settings' : 'Advanced document settings'}</Button>
      {advanced && <Stack spacing={2}>
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          {number('Chunk size (tokens)', config.processing.chunkTokens, 100, 800, value => edit(c => ({ ...c, processing: { ...c.processing, chunkTokens: value } })), 'Maximum target size. Smaller chunks focus narrow questions.')}
          {number('Overlap (tokens)', config.processing.overlapTokens, 0, config.processing.chunkTokens / 2, value => edit(c => ({ ...c, processing: { ...c.processing, overlapTokens: value } })), 'Repeats prior context within the same page.')}
          {number('Minimum chunk (characters)', config.processing.minChunkChars, 0, config.processing.chunkTokens, value => edit(c => ({ ...c, processing: { ...c.processing, minChunkChars: value } })), 'Avoid splitting into fragments that are too small.')}
          {number('Minimum indexed text (characters)', config.processing.minEmbedChars, 0, config.processing.minChunkChars, value => edit(c => ({ ...c, processing: { ...c.processing, minEmbedChars: value } })), 'Tiny pieces below this length are not embedded.')}
        </Stack>
        <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
          <TextField select size="small" label="OCR mode" value={config.processing.ocrMode} disabled={!canEdit} onChange={e => edit(c => ({ ...c, processing: { ...c.processing, ocrMode: e.target.value as 'AUTO' | 'OFF' | 'ALWAYS' } }))} sx={{ minWidth: 180 }}>
            <MenuItem value="AUTO">When text is missing</MenuItem><MenuItem value="ALWAYS">Every PDF page</MenuItem><MenuItem value="OFF">Text only</MenuItem></TextField>
          <TextField select size="small" label="OCR language" value={config.processing.ocrLanguage} disabled={!canEdit} onChange={e => edit(c => ({ ...c, processing: { ...c.processing, ocrLanguage: e.target.value } }))} sx={{ minWidth: 160 }}>
            {snapshot.ocrLanguages.map(language => <MenuItem key={language} value={language}>{language === 'eng' ? 'English' : language}</MenuItem>)}</TextField>
          {number('Scan resolution (DPI)', config.processing.ocrDpi, 72, 300, value => edit(c => ({ ...c, processing: { ...c.processing, ocrDpi: value } })), 'Higher resolution uses more memory and time.')}
        </Stack>
      </Stack>}
      {preview && !currentPreview && <Alert severity="info">Settings changed after this preview. Inspect the candidate again to see its current chunks.</Alert>}
      <ChunkPreview preview={currentPreview} error={previewError} />
    </Stack>}
    {view === 1 && <Stack spacing={2} sx={{ py: 2 }}>
      <Stack direction="row" spacing={1}><Chip label={snapshot.activeSourceVersion ? `Searchable: version ${snapshot.activeSourceVersion}` : 'Not searchable yet'} size="small" />
        <Chip label={`Candidate: version ${snapshot.sourceVersion}`} size="small" variant="outlined" /></Stack>
      {snapshot.reindexRequired && <Alert severity="warning">New preparation settings are waiting to be indexed. Current answers still use version {snapshot.activeSourceVersion}.</Alert>}
      <Typography variant="body2">Review extracted text before indexing. The current version remains searchable while the replacement is prepared.</Typography>
      <Stack direction="row" spacing={1}><Button variant="outlined" startIcon={<PreviewOutlined />} onClick={onPreview} disabled={dirty || previewBusy}>Preview candidate</Button>
        <Button variant="contained" startIcon={<PlayArrowRounded />} disabled={!canEdit || dirty || indexBusy} onClick={onIndex}>{indexBusy ? 'Submitting...' : snapshot.activeSourceVersion ? 'Reindex candidate' : 'Index document'}</Button></Stack>
      <ChunkPreview preview={currentPreview} error={previewError} />
    </Stack>}
    {view === 2 && <Stack spacing={2} sx={{ py: 2 }}>
      <Box component="details" open={!run} sx={{ borderBottom: 1, borderColor: 'divider', pb: 2 }}>
      <Typography component="summary" fontWeight={650} sx={{ cursor: 'pointer', mb: 2 }}>Test plan / {config.questions.length} questions</Typography>
      <Stack direction={{ xs: 'column', md: 'row' }} spacing={1} justifyContent="space-between"><Box><Typography fontWeight={650}>Questions your customers might ask</Typography>
        <Typography variant="body2" color="text.secondary">AI can prepare the questions. Review reference answers before treating them as expected results.</Typography></Box>
        <Button startIcon={<AddRounded />} disabled={!canEdit || config.questions.length >= 20} onClick={() => edit(c => ({ ...c, questions: [...c.questions, { id: crypto.randomUUID(), question: '', referenceAnswer: '', reviewed: false }] }))}>Add question</Button></Stack>
      <Stack spacing={2} sx={{ maxHeight: 350, overflow: 'auto', mt: 2, pr: .5 }}>
      {config.questions.map((question, index) => <Box key={question.id} sx={{ borderBottom: 1, borderColor: 'divider', pb: 2 }}>
        <Stack direction="row" spacing={1}><TextField fullWidth size="small" label={`Question ${index + 1}`} value={question.question} disabled={!canEdit} onChange={e => edit(c => ({ ...c, questions: c.questions.map(q => q.id === question.id ? { ...q, question: e.target.value, reviewed: false } : q) }))} />
          <Tooltip title="Remove question"><IconButton aria-label={`Remove question ${index + 1}`} disabled={!canEdit} onClick={() => edit(c => ({ ...c, questions: c.questions.filter(q => q.id !== question.id) }))}><DeleteOutlineRounded /></IconButton></Tooltip></Stack>
        <TextField fullWidth multiline minRows={2} size="small" label="Reference answer (optional)" value={question.referenceAnswer} sx={{ mt: 1.5 }} disabled={!canEdit} onChange={e => edit(c => ({ ...c, questions: c.questions.map(q => q.id === question.id ? { ...q, referenceAnswer: e.target.value, reviewed: false } : q) }))} />
        <FormControlLabel control={<Checkbox checked={question.reviewed} disabled={!canEdit} onChange={e => edit(c => ({ ...c, questions: c.questions.map(q => q.id === question.id ? { ...q, reviewed: e.target.checked } : q) }))} />} label="I reviewed this question and reference" />
      </Box>)}
      </Stack>
      {!config.questions.length && <Alert severity="info">No test questions yet. Choose Optimize with AI or add your own.</Alert>}
      <Box component="details" sx={{ mt: 2 }}><Typography component="summary" variant="body2" sx={{ cursor: 'pointer', mb: 2 }}>Advanced evaluation settings</Typography>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
        {number('Questions per run', config.evaluationOptions.maxQuestions, 1, 20, value => edit(c => ({ ...c, evaluationOptions: { ...c.evaluationOptions, maxQuestions: value } })), 'At most two model calls per question with the default judge.')}
        {number('Retrieved chunks', config.evaluationOptions.maxDocuments, 1, 10, value => edit(c => ({ ...c, evaluationOptions: { ...c.evaluationOptions, maxDocuments: value } })), 'Maximum excerpts for each test answer.')}
        {number('Similarity threshold', config.evaluationOptions.similarityThreshold, 0, 1, value => edit(c => ({ ...c, evaluationOptions: { ...c.evaluationOptions, similarityThreshold: value } })), 'Filters this test retrieval, not all Brain conversations.', 0.05)}
      </Stack>
      </Box>
      </Box>
      <Stack direction="row" spacing={2} flexWrap="wrap" useFlexGap>
        {number('Context allowance (characters)', config.evaluationOptions.maxContextCharacters, 1000, 16000, value => edit(c => ({ ...c, evaluationOptions: { ...c.evaluationOptions, maxContextCharacters: value } })), 'Maximum document text shared with the answer model.')}
        {number('Answer allowance (tokens)', config.evaluationOptions.answerMaxTokens, 128, 1500, value => edit(c => ({ ...c, evaluationOptions: { ...c.evaluationOptions, answerMaxTokens: value } })), 'Output ceiling per test answer.')}
      </Stack>
      <Alert severity="info">Runs use your configured models and allowance. Scores are model judgments, not a guarantee. Tests read only this document; they do not execute business actions.</Alert>
      {snapshot.reindexRequired && <Alert severity="warning">This run will test active version {snapshot.activeSourceVersion}, not the unindexed candidate.</Alert>}
      <Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap>
        <Button variant="contained" startIcon={<AutoAwesomeOutlined />} disabled={!assistant || !canEdit || dirty || !snapshot.activeSourceVersion || !config.questions.length || Boolean(activeRun) || evaluate.isPending} onClick={() => optimize(true)}>Evaluate &amp; optimize with AI</Button>
        <Button startIcon={<SaveOutlined />} disabled={!dirty || stale || !canEdit || save.isPending} onClick={() => save.mutate()}>Save test settings</Button>
        <Button variant="outlined" startIcon={<FactCheckOutlined />} disabled={!canEdit || dirty || !snapshot.activeSourceVersion || !config.questions.length || Boolean(activeRun) || evaluate.isPending} onClick={() => evaluate.mutate()}>Run evaluation</Button>
        {activeRun && <Button color="error" startIcon={<StopRounded />} disabled={cancel.isPending} onClick={() => cancel.mutate(activeRun.id)}>Stop run</Button>}
      </Stack>
      {[evaluate.error, cancel.error, evaluations.error].filter(Boolean).map((error, i) => <Alert key={i} severity="error">{message(error)}</Alert>)}
      {runs.length > 0 && <TextField size="small" select label="Evaluation run" value={run?.id ?? ''} onChange={e => setSelectedRun(e.target.value)}>
        {runs.map(item => <MenuItem key={item.id} value={item.id}>{new Date(item.createdAt).toLocaleString()} / v{item.sourceVersion} / {item.status.toLowerCase().split('_').join(' ')}</MenuItem>)}</TextField>}
      {run && <Stack spacing={1.5}><Stack direction="row" spacing={1} flexWrap="wrap" useFlexGap><Chip size="small" label={run.status.split('_').join(' ')} /><Chip size="small" variant="outlined" label={`${run.modelCalls} model calls`} /><Chip size="small" variant="outlined" label={run.providerId} /></Stack>
        {run.syntheticReferences && <Typography variant="caption" color="text.secondary">Includes unreviewed or AI-generated references.</Typography>}
        {run.cases.map(item => <Box key={item.id} sx={{ borderBottom: 1, borderColor: 'divider', py: 1.5 }}>
          <Typography fontWeight={650}>{item.question}</Typography>
          {item.error && <Alert severity="warning">{item.error}</Alert>}
          <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', my: 1 }}>{item.answer}</Typography>
          <Typography variant="caption" color="text.secondary">Retrieved {item.retrieval?.evidenceCount ?? 0} chunks</Typography>
          {Object.entries(item.evaluation?.metrics ?? {}).map(([name, metric]) => {
            const prior = previous?.providerId === run.providerId && previous.rubricVersion === run.rubricVersion
              ? previous.cases.find(c => c.id === item.id && c.question === item.question && c.referenceAnswer === item.referenceAnswer)?.evaluation?.metrics[name]?.score : undefined
            return <Box key={name} sx={{ mt: 1 }}><Typography variant="body2" fontWeight={600}>{metricLabel[name] ?? name}: {metric.score === null ? 'Not scored' : `${Math.round(metric.score * 100)}%`}
              {prior != null && metric.score != null ? ` (previous ${Math.round(prior * 100)}%)` : ''}</Typography><Typography variant="body2" color="text.secondary">{metric.explanation}</Typography></Box>
          })}
          <Box component="details" sx={{ mt: 1 }}><Typography component="summary" variant="body2" sx={{ cursor: 'pointer' }}>Inspect evaluated excerpts</Typography>
            {item.evaluatedExcerpts?.map(excerpt => <Typography key={excerpt.id} variant="body2" sx={{ borderLeft: 2, borderColor: 'divider', pl: 1.5, mt: 1, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{excerpt.content}</Typography>)}</Box>
        </Box>)}
        <Button startIcon={<AutoAwesomeOutlined />} sx={{ alignSelf: 'flex-start' }} disabled={!assistant || dirty || !canEdit || running(run)} onClick={() => optimize()}>Improve from these results</Button>
      </Stack>}
    </Stack>}
  </Box>
}

function ChunkPreview({ preview, error }: { preview?: DocumentPreviewResult; error?: string }) {
  return <>{error && <Alert severity="error">{error}</Alert>}{preview && <Stack spacing={1.5}>
    <Typography variant="subtitle2">{preview.documentCount} text sections / {preview.chunkCount} chunks / {preview.totalContentLength.toLocaleString()} characters</Typography>
    {preview.extraction && <Typography variant="caption" color="text.secondary">{preview.extraction.format.toUpperCase()} / {preview.extraction.ocrSections} OCR sections</Typography>}
    {Boolean(preview.extraction?.warnings.length) && <Alert severity="warning">Extraction notices: {preview.extraction!.warnings.join(', ')}</Alert>}
    {preview.chunksTruncated && <Typography variant="caption" color="text.secondary">Partial preview. Not all chunks are shown.</Typography>}
    {preview.chunks.map(chunk => <Box key={chunk.chunkId} sx={{ borderLeft: 3, borderColor: 'primary.main', pl: 2, py: .5 }}><Typography variant="caption" color="text.secondary">Chunk {chunk.chunkIndex + 1}/{chunk.chunkCount}{chunk.pageNumber ? ` / page ${chunk.pageNumber}` : ''}{chunk.extractionMethod ? ` / ${chunk.extractionMethod}` : ''}{chunk.truncated ? ' / excerpt' : ''}</Typography>
      <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{chunk.contentPreview}</Typography></Box>)}
  </Stack>}</>
}
