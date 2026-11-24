import { Box, Typography } from '@mui/material'
import { providerDiagnostics, providerFieldLabels } from './providerDiagnostics'

export function ProviderDiagnosticsDetails({ value }: { value: unknown }) {
  const metadata = providerDiagnostics(value)
  if (!metadata) return null
  return <Box component="details" sx={{ mt: .75, fontSize: 12 }}>
    <summary>Provider diagnostics ({metadata.observations.length} observations)</summary>
    <Box sx={{ maxHeight: 220, overflowY: 'auto', minWidth: 0 }}>
      {metadata.observations.map((observation, index) => <Box component="dl" key={index} sx={{ m: 0, py: .75, borderBottom: 1, borderColor: 'divider', display: 'grid', gridTemplateColumns: 'minmax(80px, .9fr) minmax(0, 1fr)', columnGap: 1 }}>
        {Object.entries(observation).map(([key, field]) => <Box key={key} sx={{ display: 'contents' }}>
          <Typography component="dt" variant="caption" color="text.secondary">{providerFieldLabels[key as keyof typeof providerFieldLabels]}</Typography>
          <Typography component="dd" variant="caption" sx={{ m: 0, overflowWrap: 'anywhere' }}>{field === null ? 'Unavailable' : typeof field === 'boolean' ? field ? 'Yes' : 'No' : field}</Typography>
        </Box>)}
      </Box>)}
      {metadata.droppedObservations !== undefined && <Typography variant="caption" display="block" sx={{ mt: .5 }}>Earlier observations omitted: {metadata.droppedObservations}</Typography>}
    </Box>
  </Box>
}
