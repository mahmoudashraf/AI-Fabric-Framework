import { Alert, Box, Typography } from '@mui/material'
import { stringifyExactJson } from '../lib/losslessJson'
import { changedFields, validationSummary, type ChangedField } from './turnPresentation'
import type { AssistantTurn } from './types'

export function ValidationOutcome({ turn }: { turn: AssistantTurn }) {
  if (turn.status !== 'SAVED' && turn.status !== 'PREVIEW') return null
  const result = validationSummary(turn)
  const title = result.outcome === 'attention' ? 'Needs attention'
    : result.outcome === 'passed' ? 'Configuration validation passed' : 'Validation not reported'
  return <Alert severity={result.outcome === 'attention' || result.warningCount ? 'warning' : result.outcome === 'passed' ? 'success' : 'info'} sx={{ my: 1, '& .MuiAlert-message': { minWidth: 0, overflowWrap: 'anywhere' } }}>
    <Typography variant="body2" fontWeight={600}>{turn.status === 'SAVED' ? 'Draft saved. ' : ''}{title}.</Typography>
    {(result.errorCount !== undefined || result.warningCount !== undefined) && <Typography variant="caption" display="block">
      {[result.errorCount !== undefined ? `${result.errorCount} validation errors` : '', result.warningCount !== undefined ? `${result.warningCount} warnings` : ''].filter(Boolean).join('; ')}
    </Typography>}
    {result.connectionTested === false && <Typography variant="caption" display="block">Connection not tested.</Typography>}
    {result.issues.map((issue, index) => <Box key={index} sx={{ mt: .5 }}>
      <Typography variant="body2">{issue.message}</Typography>
      {(issue.path || issue.code) && <Typography variant="caption" component="code" sx={{ display: 'block', overflowWrap: 'anywhere' }}>{[issue.path, issue.code].filter(Boolean).join(' / ')}</Typography>}
    </Box>)}
  </Alert>
}

function FieldChange({ change }: { change: ChangedField }) {
  const value = (item: unknown) => item === undefined ? 'Not present' : stringifyExactJson(item)
  return <Box component="li" sx={{ listStyle: 'none', borderBottom: 1, borderColor: 'divider', py: .75, minWidth: 0 }}>
    <Typography component="code" fontSize={12} sx={{ display: 'block', overflowWrap: 'anywhere' }}>{change.path}{change.field}</Typography>
    {change.key !== undefined && <Typography variant="caption" component="code" sx={{ display: 'block', overflowWrap: 'anywhere' }}>key: {stringifyExactJson(change.key)}</Typography>}
    <Box component="dl" sx={{ display: 'grid', gridTemplateColumns: '44px minmax(0, 1fr)', gap: .5, m: 0, mt: .5, fontSize: 12 }}>
      <Box component="dt" sx={{ color: 'text.secondary' }}>Before</Box><Box component="dd" sx={{ m: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{value(change.before)}</Box>
      <Box component="dt" sx={{ color: 'text.secondary' }}>After</Box><Box component="dd" sx={{ m: 0, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{value(change.after)}</Box>
    </Box>
  </Box>
}

export function ChangeSummary({ turn }: { turn: AssistantTurn }) {
  if (!Array.isArray(turn.diff)) return null
  const fields = changedFields(turn.diff)
  const title = turn.status === 'SAVED' ? 'Saved changes' : turn.status === 'UNDONE' ? 'Original saved changes' : 'Proposed changes'
  return <Box component="section" aria-label={title} sx={{ mt: 1, minWidth: 0, whiteSpace: 'normal' }}>
    <Typography fontSize={13} fontWeight={600}>{title}</Typography>
    {fields.length ? <>
      <Box component="ul" sx={{ p: 0, m: 0 }}>{fields.slice(0, 6).map((change, index) => <FieldChange key={index} change={change} />)}</Box>
      {fields.length > 6 && <Box component="details" sx={{ fontSize: 12, mt: .75 }}><summary>{fields.length - 6} more changed fields</summary><Box component="ul" sx={{ p: 0, m: 0 }}>{fields.slice(6).map((change, index) => <FieldChange key={index} change={change} />)}</Box></Box>}
    </> : <Typography variant="caption" color="text.secondary">{turn.diff.length ? 'No comparable value changes reported.' : 'No changed fields reported.'}</Typography>}
  </Box>
}
