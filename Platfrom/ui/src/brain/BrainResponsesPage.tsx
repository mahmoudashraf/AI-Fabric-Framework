import AltRouteRoundedIcon from '@mui/icons-material/AltRouteRounded'
import ChatBubbleOutlineRoundedIcon from '@mui/icons-material/ChatBubbleOutlineRounded'
import OpenInFullRoundedIcon from '@mui/icons-material/OpenInFullRounded'
import PersonOutlineRoundedIcon from '@mui/icons-material/PersonOutlineRounded'
import PlayArrowRoundedIcon from '@mui/icons-material/PlayArrowRounded'
import RecordVoiceOverRoundedIcon from '@mui/icons-material/RecordVoiceOverRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import SubjectRoundedIcon from '@mui/icons-material/SubjectRounded'
import { Alert, Box, Button, IconButton, Link, MenuItem, Stack, Tab, Tabs, TextField, Tooltip, Typography } from '@mui/material'
import { alpha } from '@mui/material/styles'
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useEffect, useMemo, useRef, useState } from 'react'
import { Link as RouterLink, useLocation } from 'react-router-dom'
import {
  composeDeploymentAiBehaviour, fetchDeploymentAiBehaviourProfiles, fetchDeploymentDraft, updateDeploymentDraft,
  type AiBehaviourGroupKey, type AiBehaviourSelections,
} from '../api/platformApi'
import { aiBehaviourPresetSelections, promptConfigIdentity, promptConfigRecord, readAiBehaviour } from '../aiBehaviourProfiles'
import { PromptsPage } from '../pages/PromptsPage'
import { useDeploymentWorkspace } from '../workspace/DeploymentWorkspaceContext'
import { useBrainEditor, useBrainWorkspace } from './BrainWorkspaceContext'
import { BrainPageHeader, brainPaneSx } from './BrainPageLayout'

const profileFields = [
  { key: 'tone', label: 'Tone', Icon: RecordVoiceOverRoundedIcon },
  { key: 'answerBreadth', label: 'Use of general advice', Icon: OpenInFullRoundedIcon },
  { key: 'answerLength', label: 'Answer length', Icon: SubjectRoundedIcon },
  { key: 'nextSteps', label: 'Helpful next steps', Icon: AltRouteRoundedIcon },
] as const

function optionLabel(key: AiBehaviourGroupKey, id: string, fallback: string) {
  if (key === 'answerBreadth') return ({ grounded: 'Your information only', advisory: 'Helpful guidance', exploratory: 'Broader ideas' } as Record<string, string>)[id] ?? fallback
  if (key === 'nextSteps' && id === 'contextual') return 'When useful'
  return fallback
}

export function BrainResponsesPage() {
  const { brainId, href } = useBrainWorkspace()
  const { pathname } = useLocation()
  const suffix = pathname.split('/responses')[1]?.replace(/^\/+|\/+$/g, '') ?? ''
  const view = suffix === 'instructions' ? 'instructions' : suffix === 'advanced' ? 'advanced' : 'reply-style'
  return <Box sx={{ minWidth: 0, display: 'flex', flexDirection: 'column', height: '100%', minHeight: 0 }}>
    <BrainPageHeader title="How your AI answers" />
    <Tabs value={view} aria-label="Responses views" sx={{ borderBottom: 1, borderColor: 'divider', flexShrink: 0, mb: 2 }}>
      <Tab value="reply-style" label="Reply style" component={RouterLink} to={href('responses')} />
      <Tab value="instructions" label="Instructions" component={RouterLink} to={href('responses/instructions')} />
      <Tab value="advanced" label="Advanced" component={RouterLink} to={href('responses/advanced')} />
    </Tabs>
    {view === 'reply-style' ? <ReplyStyleEditor key={brainId} />
      : <Box sx={{ ...brainPaneSx, flex: 1 }}><PromptsPage key={brainId} workspaceView={view} /></Box>}
  </Box>
}

function ReplyStyleEditor() {
  const { brainId, href } = useBrainWorkspace()
  const { workspace } = useDeploymentWorkspace()
  const queryClient = useQueryClient()
  const draftQuery = useQuery({ queryKey: ['deployment-draft', brainId], queryFn: () => fetchDeploymentDraft(brainId), enabled: Boolean(brainId) })
  const catalogue = useQuery({ queryKey: ['deployment-ai-behaviour-profiles'], queryFn: fetchDeploymentAiBehaviourProfiles })
  const [base, setBase] = useState<Record<string, unknown> | null>(null)
  const [selections, setSelections] = useState<AiBehaviourSelections>({})
  const [conflict, setConflict] = useState(false)
  const localEdits = useRef(false)
  const dirty = Object.keys(selections).length > 0
  const canEdit = workspace?.access.canEdit ?? false
  const metadata = readAiBehaviour(base)
  const savedSelections = aiBehaviourPresetSelections(base)
  const effectiveSelections = { ...savedSelections, ...selections }

  useEffect(() => {
    if (!draftQuery.data || localEdits.current) return
    setBase(promptConfigRecord(draftQuery.data.promptConfig))
  }, [draftQuery.data])

  const saveMutation = useMutation({
    mutationFn: async () => {
      if (!canEdit || !draftQuery.data || !base) throw new Error('No editable draft available.')
      const latest = await fetchDeploymentDraft(brainId)
      if (latest.id !== draftQuery.data.id || promptConfigIdentity(latest.promptConfig) !== promptConfigIdentity(base)) {
        setConflict(true)
        throw new Error('The saved response settings changed while you were editing. Reload the saved draft before choosing your changes again.')
      }
      const composed = await composeDeploymentAiBehaviour(brainId, { promptConfig: base, selections })
      return updateDeploymentDraft(latest.id, { promptConfig: composed.promptConfig })
    },
    onSuccess: draft => {
      localEdits.current = false
      setBase(promptConfigRecord(draft.promptConfig))
      setSelections({})
      setConflict(false)
      queryClient.setQueryData(['deployment-draft', brainId], draft)
      void queryClient.invalidateQueries({ queryKey: ['deployment-workspace', brainId] })
    },
  })

  const editorState = useMemo(() => ({
    dirty, busy: saveMutation.isPending, canSave: canEdit && Boolean(base && catalogue.data) && dirty && !conflict,
    save: canEdit ? () => saveMutation.mutateAsync().catch(() => undefined) : undefined,
    discard: canEdit ? () => {
      localEdits.current = false
      setSelections({}); setConflict(false); saveMutation.reset()
      setBase(draftQuery.data ? promptConfigRecord(draftQuery.data.promptConfig) : null)
    } : undefined,
    message: !canEdit ? 'Read-only access. Response changes require editor permission.'
      : saveMutation.isSuccess && !dirty ? 'Draft saved; live configuration unchanged'
        : dirty ? 'Unsaved changes' : 'Editing shared draft',
    error: saveMutation.isError ? saveMutation.error.message : null,
  }), [dirty, saveMutation.isPending, saveMutation.isSuccess, saveMutation.isError, saveMutation.error,
    canEdit, base, catalogue.data, conflict, draftQuery.data, saveMutation.mutateAsync, saveMutation.reset])
  useBrainEditor(editorState)

  function choose(key: AiBehaviourGroupKey, value: string) {
    setSelections(previous => {
      const next = { ...previous }
      if (!value || value === savedSelections[key]) delete next[key]
      else next[key] = value
      localEdits.current = Object.keys(next).length > 0
      return next
    })
    saveMutation.reset()
  }

  return <Box sx={{ ...brainPaneSx, flex: 1 }}>
    {draftQuery.isPending && <Typography role="status">Loading response settings...</Typography>}
    {draftQuery.isError && <Alert severity="error" action={<Button onClick={() => draftQuery.refetch()}>Retry</Button>}>
      {draftQuery.error.message}
    </Alert>}
    {catalogue.isError && <Alert severity="error" action={<Button onClick={() => catalogue.refetch()}>Retry</Button>}>{catalogue.error.message}</Alert>}
    {conflict && <Alert severity="warning" sx={{ mb: 2 }} action={<Button disabled={draftQuery.isFetching} onClick={async () => {
      const result = await draftQuery.refetch()
      if (!result.data || result.isError) return
      localEdits.current = false
      setBase(promptConfigRecord(result.data.promptConfig)); setSelections({}); setConflict(false); saveMutation.reset()
    }}>Reload saved draft</Button>}>Your choices are retained. Reloading replaces them with the latest saved settings.</Alert>}
    {!canEdit && <Alert severity="info" sx={{ mb: 2 }}>Read-only access. Response changes require editor permission.</Alert>}
    <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', lg: 'minmax(0, 1fr) minmax(0, 1fr)' }, gap: { xs: 3, lg: 4 } }}>
      <Box component="section" aria-label="Reply style" sx={{ minWidth: 0 }}>
        <Stack direction="row" alignItems="center" justifyContent="space-between" sx={{ mb: 1 }}>
          <Typography component="h2" variant="subtitle1" sx={{ fontWeight: 650 }}>Reply style</Typography>
          <Tooltip title="Choose default profiles"><span><IconButton size="small" aria-label="Choose default profiles"
            disabled={!canEdit || !base || !catalogue.data || saveMutation.isPending || conflict} onClick={() => {
              const next = Object.fromEntries(Object.entries(catalogue.data!.defaults).filter(([key, value]) => value !== savedSelections[key as AiBehaviourGroupKey]))
              localEdits.current = Object.keys(next).length > 0
              setSelections(next); saveMutation.reset()
            }}><RefreshRoundedIcon sx={{ fontSize: 18 }} /></IconButton></span></Tooltip>
        </Stack>
        {profileFields.map(({ key, label, Icon }) => {
          const group = catalogue.data?.groups.find(group => group.key === key)
          const selected = effectiveSelections[key] ?? ''
          const selectedOption = group?.options.find(option => option.id === selected)
          const currentLabel = metadata?.[key]?.presetId === 'custom' ? 'Custom instructions' : 'Current instructions'
          return <Box key={key} sx={{ mb: 1.75, minWidth: 0 }}>
            <Stack direction="row" spacing={1} alignItems="center" sx={{ mb: 0.75 }}>
              <Icon sx={{ fontSize: 18, color: 'text.secondary' }} />
              <Typography component="label" htmlFor={`response-${key}`} variant="body2" sx={{ fontWeight: 600 }}>{label}</Typography>
            </Stack>
            <TextField id={`response-${key}`} select fullWidth size="small" value={selected}
              disabled={!canEdit || !base || !group || saveMutation.isPending || conflict}
              SelectProps={{ displayEmpty: true, SelectDisplayProps: { 'aria-label': label },
                renderValue: value => value ? optionLabel(key, String(value), selectedOption?.label ?? String(value)) : currentLabel }}
              sx={{ '& .MuiSelect-select': { whiteSpace: 'normal', overflowWrap: 'anywhere', minHeight: '20px' } }}
              onChange={event => choose(key, event.target.value)}>
              <MenuItem value="">{currentLabel}</MenuItem>
              {selected && !selectedOption && <MenuItem value={selected}>{selected} (saved revision)</MenuItem>}
              {group?.options.map(option => <MenuItem key={option.id} value={option.id}>{optionLabel(key, option.id, option.label)}</MenuItem>)}
            </TextField>
            {(key === 'answerBreadth' || !selectedOption) && <Typography variant="caption" color="text.secondary" sx={{ display: 'block', mt: 0.6, minHeight: '18px' }}>
              {selectedOption?.description ?? (metadata?.[key]?.presetId === 'custom' ? 'Custom instructions retained.' : 'Current instructions retained.')}
            </Typography>}
          </Box>
        })}
        <Link component={RouterLink} to={href('responses/instructions')} sx={{ fontSize: 14 }}>View profile instructions</Link>
      </Box>
      <Box component="section" aria-label="Illustrative preview" sx={{ minWidth: 0, borderLeft: { lg: 1 }, borderTop: { xs: 1, lg: 0 },
        borderColor: 'divider', pl: { lg: 4 }, pt: { xs: 2, lg: 0 } }}>
        <Typography variant="overline" color="text.secondary">Illustrative preview</Typography>
        <Typography component="h2" variant="subtitle1" sx={{ fontWeight: 650, mb: 2 }}>What a customer could see</Typography>
        <Stack spacing={2}>
          <Stack direction="row" spacing={1.5} alignItems="flex-start">
            <PersonOutlineRoundedIcon sx={{ color: 'text.secondary', mt: 1 }} />
            <Box sx={{ p: 1.5, borderRadius: 1, bgcolor: 'action.selected', flex: 1, minWidth: 0 }}>
              <Typography variant="body2">Which option would suit my needs?</Typography>
            </Box>
          </Stack>
          <Stack direction="row" spacing={1.5} alignItems="flex-start">
            <ChatBubbleOutlineRoundedIcon sx={{ color: 'primary.main', mt: 1 }} />
            <Box sx={{ p: 2, border: 1, borderColor: 'divider', borderRadius: 1, flex: 1, minWidth: 0,
              bgcolor: theme => alpha(theme.palette.primary.main, 0.035) }}>
              <StyleIllustration selections={effectiveSelections} />
            </Box>
          </Stack>
          <Box><Button component={RouterLink} to={href('try')} variant="outlined" startIcon={<PlayArrowRoundedIcon />}>Try with your Brain</Button></Box>
          <Typography variant="caption" color="text.secondary">Style example, not a live result. Custom instructions are not simulated.</Typography>
          <Typography variant="caption" color="text.secondary">General advice does not enable internet access, change sampling or verify business facts.</Typography>
        </Stack>
      </Box>
    </Box>
  </Box>
}

function StyleIllustration({ selections }: { selections: AiBehaviourSelections }) {
  const opening: Record<string, string> = {
    professional: 'Here are the options and the key differences to consider.',
    warm: "Let's work through the options that fit what you need.",
    consultative: 'The best fit depends on your priorities. We can compare the relevant trade-offs.',
    technical: 'Compare the supplied specifications against your requirements and constraints.',
  }
  const breadth: Record<string, string> = {
    grounded: 'Specific recommendations need your approved information. Missing details stay visible.',
    advisory: 'As general guidance, consider your priorities and practical constraints. This is not a verified business offering.',
    exploratory: 'A broader idea is to compare alternatives beyond the current options. These ideas are not verified offerings.',
  }
  return <Stack spacing={1.25}>
    <Typography variant="body2">{opening[selections.tone ?? ''] ?? 'Your current or custom tone is not simulated in this example.'}</Typography>
    <Typography variant="body2">{breadth[selections.answerBreadth ?? ''] ?? 'Your current guidance instructions remain unchanged.'}</Typography>
    {selections.answerLength === 'detailed' && <Typography variant="body2">A detailed answer can separate the comparison, assumptions and evidence gaps before explaining the recommendation.</Typography>}
    {selections.answerLength === 'balanced' && <Typography variant="body2">Focus on the main difference and the evidence needed for the next decision.</Typography>}
    {selections.nextSteps === 'contextual' && <Typography variant="body2" sx={{ pt: 1, borderTop: 1, borderColor: 'divider' }}>A useful next step could be to identify your most important requirement.</Typography>}
  </Stack>
}
