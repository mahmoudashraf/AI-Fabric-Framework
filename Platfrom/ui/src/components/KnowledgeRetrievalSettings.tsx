import { Alert, Box, Checkbox, FormControlLabel, Link, MenuItem, Stack, Switch, TextField, ToggleButton, ToggleButtonGroup, Typography } from '@mui/material'
import { Link as RouterLink } from 'react-router-dom'
import { ConfigurationDetails } from './ConfigurationSurface'
import { RerankingUsageStatus } from './RerankingUsageStatus'

type Props = { value: string; vectorStrategy: string; disabled: boolean; brainId?: string | null; targetProfileId?: string
  inheritedMaxResults?: number; inheritedMaxContextCharacters?: number; onChange: (value: string) => void }
const defaults = { vectorCandidates: 20, lexicalCandidates: 20, maxCandidates: 40, maxResults: 6, maxContextCharacters: 8000, rankConstant: 60, failurePolicy: 'FAIL' }
const rankingDefaults = { provider: 'cohere', model: 'rerank-v5.0-fast', enabled: false, externalProcessingApproved: false,
  maxCandidates: 40, maxCharactersPerCandidate: 4000, maxTotalCharacters: 160000, maxTokensPerDocument: 1024, timeoutMs: 5000, failurePolicy: 'FAIL' }

export function KnowledgeRetrievalSettings({ value, vectorStrategy, disabled, brainId, targetProfileId,
  inheritedMaxResults, inheritedMaxContextCharacters, onChange }: Props) {
  const inherited = { ...defaults, maxResults: inheritedMaxResults ?? defaults.maxResults,
    maxContextCharacters: inheritedMaxContextCharacters ?? defaults.maxContextCharacters }
  let profile: Record<string, unknown> = {}
  try {
    const parsed: unknown = value ? JSON.parse(value) : {}
    if (parsed && typeof parsed === 'object' && !Array.isArray(parsed)) profile = parsed as Record<string, unknown>
    else throw new Error('Invalid search profile')
  } catch {
    return <Alert severity="error">Search settings could not be read. The saved configuration is unchanged.</Alert>
  }
  const mode = profile.enabled === false ? '' : typeof profile.mode === 'string' ? profile.mode : ''
  if (profile.reranking != null && (typeof profile.reranking !== 'object' || Array.isArray(profile.reranking))) {
    return <Alert severity="error">Reranking settings could not be read. The saved configuration is unchanged.</Alert>
  }
  const ranking = { ...rankingDefaults, ...(profile.reranking as Record<string, unknown> | undefined) }
  const capable = vectorStrategy === 'weaviate'
  const update = (patch: Record<string, unknown>) => onChange(JSON.stringify({ ...inherited, ...profile, ...patch }))
  const updateRanking = (patch: Record<string, unknown>) => update({ reranking: { ...ranking, ...patch } })
  const rankingNumber = (key: keyof typeof rankingDefaults, label: string, maximum: number) => {
    const current = ranking[key]
    const error = typeof current !== 'number' || !Number.isInteger(current) || current < 1 || current > maximum
    return <TextField fullWidth size="small" label={label} type="number" value={current} disabled={disabled}
      error={error} helperText={error ? `Choose a whole number from 1 to ${maximum}.` : undefined}
      inputProps={{ min: 1, max: maximum }} onChange={event => updateRanking({ [key]: event.target.value === '' ? '' : Number(event.target.value) })} />
  }
  const numeric = (key: keyof typeof defaults, label: string, maximum: number, minimum = 1) => {
    const current = profile[key] ?? inherited[key]
    const error = typeof current !== 'number' || !Number.isInteger(current) || current < minimum || current > maximum
    return <TextField fullWidth size="small" label={label} type="number" value={current} disabled={disabled}
      error={error} helperText={error ? `Choose a whole number from ${minimum} to ${maximum}.` : undefined}
      inputProps={{ min: minimum, max: maximum }}
      onChange={event => update({ [key]: event.target.value === '' ? '' : Number(event.target.value) })} />
  }
  return <Box component="section" aria-label="Knowledge search approach">
    <Stack spacing={2}>
      <Typography variant="subtitle1" fontWeight={650}>Find the right information</Typography>
      <ToggleButtonGroup exclusive value={mode} size="small" disabled={disabled}
        onChange={(_, next: string | null) => { if (next !== null) update(next === '' ? { enabled: false } : { enabled: true, mode: next }) }}
        aria-label="Search approach" sx={{ flexWrap: 'wrap', '& .MuiToggleButton-root': { textTransform: 'none', px: 2 } }}>
        <ToggleButton value="">Keep existing search</ToggleButton>
        <ToggleButton value="DENSE">Meaning</ToggleButton>
        <ToggleButton value="HYBRID" disabled={!capable && mode !== 'HYBRID'}>Words and meaning</ToggleButton>
        {mode === 'KEYWORD' && <ToggleButton value="KEYWORD">Words only</ToggleButton>}
      </ToggleButtonGroup>
      {!capable && <Alert severity={mode === 'HYBRID' || mode === 'KEYWORD' ? 'warning' : 'info'}>
        This service currently supports meaning search. Combined words and meaning search is available for Weaviate sources with a ready text index.
      </Alert>}
      {capable && mode === 'HYBRID' && <Typography variant="body2" color="text.secondary">
        Combines exact terms with meaning. Each selected source must have a ready text index; access and approved-document checks remain unchanged.
      </Typography>}
      {mode && <>
        <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
          {numeric('maxCandidates', 'Maximum candidate results', 200)}
          {numeric('maxResults', 'Evidence items for AI', Number(profile.maxCandidates ?? defaults.maxCandidates))}
        </Box>
        <FormControlLabel label="Prioritize relevant results" control={<Switch checked={ranking.enabled === true}
          disabled={disabled} onChange={event => updateRanking({ enabled: event.target.checked })} />} />
        {ranking.enabled === true && <Stack spacing={2}>
          <TextField select fullWidth size="small" label="Reranking model" value={String(ranking.model)} disabled={disabled}
            onChange={event => updateRanking({ provider: 'cohere', model: event.target.value })}>
            <MenuItem value="rerank-v5.0-fast">Cohere Rerank 5 Fast</MenuItem>
            <MenuItem value="rerank-v5.0-pro">Cohere Rerank 5 Pro</MenuItem>
            {!['rerank-v5.0-fast', 'rerank-v5.0-pro'].includes(String(ranking.model)) && <MenuItem value={String(ranking.model)}>{String(ranking.model)} (saved)</MenuItem>}
          </TextField>
          <FormControlLabel sx={{ alignItems: 'flex-start', '& .MuiFormControlLabel-label': { pt: 1 } }}
            label="Allow Cohere to process the question and selected, authorized search excerpts."
            control={<Checkbox checked={ranking.externalProcessingApproved === true} disabled={disabled}
              onChange={event => updateRanking({ externalProcessingApproved: event.target.checked })} />} />
          {ranking.externalProcessingApproved !== true && <Alert severity="warning">External processing approval is required before enabling reranking.</Alert>}
          <Typography variant="body2" color="text.secondary">Requires a Brain-owned reranking key and a separate approved allowance. Saving does not activate the service.
            {brainId && <> <Link component={RouterLink} to={`/smart-brains/${encodeURIComponent(brainId)}/secrets`}>Keys &amp; secrets</Link></>}
          </Typography>
          {brainId && <RerankingUsageStatus brainId={brainId} targetProfileId={targetProfileId} />}
        </Stack>}
        <ConfigurationDetails label="Advanced retrieval allowances">
          <Stack spacing={2}>
            <Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
              {numeric('vectorCandidates', 'Meaning candidate allowance', 100)}
              {numeric('lexicalCandidates', 'Word candidate allowance', 100)}
              {numeric('rankConstant', 'Reciprocal-rank constant', 1000)}
              {numeric('maxContextCharacters', 'Evidence context limit (characters)', 200000, 256)}
            </Box>
            <TextField select fullWidth size="small" label="If word search is unavailable" value={String(profile.failurePolicy ?? 'FAIL')}
              disabled={disabled} onChange={event => update({ failurePolicy: event.target.value })}>
              <MenuItem value="FAIL">Stop retrieval</MenuItem>
              <MenuItem value="VISIBLE_DENSE_FALLBACK">Use meaning and report issue</MenuItem>
            </TextField>
            {ranking.enabled === true && <><TextField select fullWidth size="small" label="If reranking is temporarily unavailable"
              value={String(ranking.failurePolicy)} disabled={disabled} onChange={event => updateRanking({ failurePolicy: event.target.value })}
              helperText="Authentication, allowance and invalid-result failures always stop reranking.">
              <MenuItem value="FAIL">Stop retrieval</MenuItem>
              <MenuItem value="VISIBLE_PRE_RERANK_FALLBACK">Keep search order and report issue</MenuItem>
            </TextField><Box sx={{ display: 'grid', gap: 2, gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' } }}>
              {rankingNumber('maxCandidates', 'Candidates to rerank', 100)}
              {rankingNumber('maxCharactersPerCandidate', 'Characters per excerpt', 16000)}
              {rankingNumber('maxTotalCharacters', 'Total excerpt characters', 200000)}
              {rankingNumber('maxTokensPerDocument', 'Reranker document token limit', 4096)}
              {rankingNumber('timeoutMs', 'Reranking timeout (ms)', 30000)}
            </Box></>}
          </Stack>
        </ConfigurationDetails>
      </>}
    </Stack>
  </Box>
}
