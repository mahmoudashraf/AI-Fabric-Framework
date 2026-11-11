import { Box, Typography } from '@mui/material'
import { stringifyExactJson } from '../lib/losslessJson'
import { importReviewRows } from './importReview'
import type { McpActionChange } from './types'

export function McpImportReview({ changes }: { changes: McpActionChange[] }) {
  return <Box component="details" sx={{ my: 1, fontSize: 13, minWidth: 0 }}><summary>Saved changes ({changes.length})</summary>
    {changes.map(change => { const rows = importReviewRows(change); return <Box component="details" key={change.actionName} sx={{ py: 1, minWidth: 0 }}><summary>{change.changeType === 'CREATED' ? 'Created' : 'Updated'}: {change.toolName}</summary>
      <Box component="section" aria-label={`${change.toolName} saved settings`} sx={{ my: 1 }}>
        {rows.map(row => <Box component="dl" key={row.key} sx={{ display: 'grid', gridTemplateColumns: { xs: 'repeat(2, minmax(0, 1fr))', sm: 'minmax(100px, .8fr) repeat(2, minmax(0, 1fr))' }, gap: 1, m: 0, py: 1, borderBottom: 1, borderColor: 'divider', '& dd': { m: 0, minWidth: 0, overflowWrap: 'anywhere', whiteSpace: 'pre-wrap', fontSize: 12 } }}>
          <Typography component="dt" variant="body2" fontWeight={600} sx={{ gridColumn: { xs: '1 / -1', sm: 'auto' }, overflowWrap: 'anywhere' }}>{row.label}</Typography>
          <Box component="dd"><Typography component="span" display="block" variant="caption" color="text.secondary">Before</Typography><Typography component="span" variant="body2" display="block">{row.before}</Typography></Box>
          <Box component="dd"><Typography component="span" display="block" variant="caption" color="text.secondary">After</Typography><Typography component="span" variant="body2" display="block">{row.after}</Typography></Box>
        </Box>)}
        {!rows.length ? <Typography variant="caption" color="text.secondary">No projected action-setting changes. Exact saved snapshots remain available below.</Typography> : null}
      </Box>
      <Box component="details" sx={{ mt: 1 }}><summary>Advanced snapshots</summary>
        <Typography variant="caption" color="text.secondary" sx={{ overflowWrap: 'anywhere' }}>Action reference: {change.actionName}</Typography>
        <Box sx={{ display: 'grid', gridTemplateColumns: { xs: 'minmax(0, 1fr)', sm: 'repeat(2, minmax(0, 1fr))' }, gap: 1, mt: 1 }}>
          {[{ label: 'Before', value: change.before }, { label: 'After', value: change.after }].map(side => <Box key={side.label} sx={{ minWidth: 0 }}><Typography variant="caption" fontWeight={600}>{side.label}</Typography><Box component="pre" tabIndex={0} aria-label={`${change.toolName} ${side.label.toLowerCase()}`} sx={{ p: 1, m: 0, mt: .5, maxHeight: 240, overflow: 'auto', bgcolor: 'action.hover', fontSize: 11, whiteSpace: 'pre-wrap', overflowWrap: 'anywhere' }}>{stringifyExactJson(side.value, 2)}</Box></Box>)}
        </Box>
      </Box>
    </Box> })}
  </Box>
}
