import ExpandMoreRoundedIcon from '@mui/icons-material/ExpandMoreRounded'
import RefreshRoundedIcon from '@mui/icons-material/RefreshRounded'
import { Accordion, AccordionDetails, AccordionSummary, Alert, Box, Button, Chip, Dialog, DialogActions,
  DialogContent, DialogTitle, MenuItem, Stack, TextField, Typography } from '@mui/material'
import { useQuery } from '@tanstack/react-query'
import { useEffect, useRef, useState } from 'react'
import { composeDeploymentAiBehaviour, fetchDeploymentAiBehaviourProfiles,
  type AiBehaviourComposition, type AiBehaviourGroupKey, type AiBehaviourSelections } from '../api/platformApi'
import { aiBehaviourPresetSelections, aiBehaviourReviewInput, formatAiBehaviourChange, promptConfigIdentity,
  readAiBehaviour } from '../aiBehaviourProfiles'

type Props = {
  deploymentId: string
  promptConfig: Record<string, unknown>
  canEdit: boolean
  onApply: (promptConfig: Record<string, unknown>) => void
  onPendingChange: (pending: boolean) => void
}

export function AiBehaviourEditor({ deploymentId, promptConfig, canEdit, onApply, onPendingChange }: Props) {
  const catalogue = useQuery({ queryKey: ['deployment-ai-behaviour-profiles'], queryFn: fetchDeploymentAiBehaviourProfiles })
  const [selections, setSelections] = useState<AiBehaviourSelections>({})
  const [instructionEdits, setInstructionEdits] = useState<Partial<Record<AiBehaviourGroupKey, string>>>({})
  const [review, setReview] = useState<AiBehaviourComposition | null>(null)
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState('')
  const requestSequence = useRef(0)
  const metadata = readAiBehaviour(promptConfig)
  const currentSelections = aiBehaviourPresetSelections(promptConfig)
  const pending = Object.keys(selections).length > 0 || Object.keys(instructionEdits).length > 0
  const configIdentity = promptConfigIdentity(promptConfig)
  const metadataIdentity = promptConfigIdentity(promptConfig.aiBehaviour)

  useEffect(() => {
    setSelections({}); setInstructionEdits({})
  }, [metadataIdentity, deploymentId])
  useEffect(() => {
    requestSequence.current++
    setReview(null); setError(''); setBusy(false)
  }, [configIdentity, deploymentId])
  useEffect(() => { onPendingChange(pending) }, [onPendingChange, pending])
  useEffect(() => () => { requestSequence.current++ }, [])

  async function reviewChanges() {
    const sequence = ++requestSequence.current
    setBusy(true); setError('')
    try {
      const result = await composeDeploymentAiBehaviour(deploymentId, aiBehaviourReviewInput(promptConfig, selections, instructionEdits))
      if (sequence === requestSequence.current) setReview(result)
    } catch (cause) {
      if (sequence === requestSequence.current) setError(cause instanceof Error ? cause.message : 'Could not prepare behaviour changes.')
    } finally {
      if (sequence === requestSequence.current) setBusy(false)
    }
  }

  return <Box component="section" aria-label="AI behaviour" sx={{ minWidth: 0 }}>
    <Stack spacing={2}>
      <Stack direction="row" spacing={1} alignItems="center" flexWrap="wrap" useFlexGap>
        <Typography variant="h6">AI behaviour</Typography>
        <Chip size="small" label={metadata ? 'Editor configuration' : 'Current instructions / not yet configured'} variant="outlined" />
        {pending && <Chip size="small" label="Choices not applied to editor" color="warning" />}
      </Stack>
      {catalogue.isPending && <Typography variant="body2" color="text.secondary">Loading behaviour profiles...</Typography>}
      {catalogue.isError && <Alert severity="error" action={<Button onClick={() => catalogue.refetch()}>Retry</Button>}>
        {catalogue.error instanceof Error ? catalogue.error.message : 'Could not load behaviour profiles.'}
      </Alert>}
      <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))', lg: 'repeat(4, minmax(0, 1fr))' } }}>
        {catalogue.data?.groups.map(group => {
          const selected = instructionEdits[group.key] !== undefined ? '' : selections[group.key] ?? currentSelections[group.key] ?? ''
          const option = group.options.find(item => item.id === selected)
          const isCustom = instructionEdits[group.key] !== undefined || metadata?.[group.key]?.presetId === 'custom'
          return <Box key={group.key} sx={{ minWidth: 0 }}>
            <TextField select fullWidth label={group.label} value={selected}
              InputLabelProps={{ shrink: true }}
              SelectProps={{ displayEmpty: true,
                SelectDisplayProps: { 'aria-label': group.label, 'aria-labelledby': undefined },
                renderValue: value => value ? group.options.find(item => item.id === value)?.label ?? String(value)
                  : isCustom ? 'Custom instructions' : 'Current instructions',
              }}
              disabled={!canEdit || !deploymentId || busy} onChange={event => {
                setSelections(previous => {
                  if (!event.target.value) { const next = { ...previous }; delete next[group.key]; return next }
                  return { ...(!metadata ? catalogue.data!.defaults : {}), ...previous, [group.key]: event.target.value }
                })
                setInstructionEdits(previous => { const next = { ...previous }; delete next[group.key]; return next })
              }}>
              <MenuItem value="">{isCustom ? 'Custom instructions' : 'Current instructions'}</MenuItem>
              {!option && selected && <MenuItem value={selected}>{selected} (saved revision)</MenuItem>}
              {group.options.map(item => <MenuItem key={item.id} value={item.id}>{item.label}</MenuItem>)}
            </TextField>
            <Typography variant="body2" color="text.secondary" sx={{ mt: 1 }}>
              {option?.description ?? (isCustom ? 'Custom instructions retained.' : 'Existing instructions stay unchanged until you choose a profile.')}
            </Typography>
            {option?.examples?.map(example => <Typography key={example} variant="caption" component="p" color="text.secondary">{example}</Typography>)}
          </Box>
        })}
      </Box>
      {error && <Alert severity="error">{error}</Alert>}
      <Stack direction="row" spacing={1} useFlexGap flexWrap="wrap">
        <Button variant="contained" disabled={!canEdit || !deploymentId || !pending || busy} onClick={reviewChanges}>
          {busy ? 'Preparing changes...' : 'Review changes'}
        </Button>
        <Button variant="outlined" startIcon={<RefreshRoundedIcon />} disabled={!canEdit || !catalogue.data || busy}
          onClick={() => { setSelections({ ...catalogue.data!.defaults }); setInstructionEdits({}) }}>Choose defaults</Button>
        {pending && <Button disabled={busy} onClick={() => { setSelections({}); setInstructionEdits({}); setError('') }}>Discard choices</Button>}
      </Stack>
      <Accordion disableGutters sx={{ boxShadow: 'none', border: '1px solid', borderColor: 'divider', '&:before': { display: 'none' } }}>
        <AccordionSummary expandIcon={<ExpandMoreRoundedIcon />}><Typography variant="subtitle2">Advanced profile instructions</Typography></AccordionSummary>
        <AccordionDetails>
          <Stack spacing={2}>
            {!metadata && <Typography variant="body2" color="text.secondary">No behaviour profiles applied. Your base prompts remain unchanged.</Typography>}
            {catalogue.data?.groups.map(group => {
              const profile = metadata?.[group.key]
              if (!profile) return null
              return <TextField key={group.key} fullWidth multiline minRows={3} label={`${group.label} instructions`}
                value={instructionEdits[group.key] ?? profile.instructions} disabled={!canEdit || busy}
                helperText={`${profile.presetId} / revision ${profile.revision}`}
                onChange={event => {
                  setInstructionEdits(previous => ({ ...previous, [group.key]: event.target.value }))
                  setSelections(previous => { const next = { ...previous }; delete next[group.key]; return next })
                }} />
            })}
          </Stack>
        </AccordionDetails>
      </Accordion>
    </Stack>
    <Dialog open={Boolean(review)} onClose={() => setReview(null)} fullWidth maxWidth="md" aria-labelledby="ai-behaviour-review-title">
      <DialogTitle id="ai-behaviour-review-title">Review AI behaviour changes</DialogTitle>
      <DialogContent dividers>
        <Stack spacing={2}>
          <Alert severity="info">Apply updates the editor only. Save the draft, then publish and apply a release to change the live experience.</Alert>
          {review?.customInstructionsPreserved && <Alert severity="warning">Your custom base instructions are preserved verbatim. Check that they agree with the selected behaviour profiles.</Alert>}
          {review?.changes.length === 0 && <Typography>No compiled prompt changes. Profile selections will still be recorded in the editor.</Typography>}
          {review?.changes.map((change, index) => <Box key={`${change.key}-${index}`} sx={{ minWidth: 0 }}>
            <Typography variant="subtitle2" sx={{ mb: 1, overflowWrap: 'anywhere' }}>{change.key}</Typography>
            <Box sx={{ display: 'grid', gap: 1, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
              {([['Before', change.before], ['After', change.after]] as const).map(([label, value]) => <Box key={label} sx={{ minWidth: 0 }}>
                <Typography variant="caption" color="text.secondary">{label}</Typography>
                <Box component="pre" sx={{ m: 0, p: 1.5, fontSize: 12, bgcolor: 'action.hover', borderRadius: 1,
                  whiteSpace: 'pre-wrap', overflowWrap: 'anywhere', maxHeight: 260, overflow: 'auto' }}>{formatAiBehaviourChange(value)}</Box>
              </Box>)}
            </Box>
          </Box>)}
        </Stack>
      </DialogContent>
      <DialogActions sx={{ flexWrap: 'wrap' }}>
        <Button onClick={() => setReview(null)}>Cancel</Button>
        <Button variant="contained" disabled={!canEdit || !review} onClick={() => {
          if (!review) return
          onApply(review.promptConfig)
          setSelections({}); setInstructionEdits({}); setReview(null)
        }}>Apply to editor</Button>
      </DialogActions>
    </Dialog>
  </Box>
}
