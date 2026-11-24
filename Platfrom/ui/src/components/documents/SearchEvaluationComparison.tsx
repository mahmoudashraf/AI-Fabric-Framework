import { Alert, Box, Button, Chip, Stack, Typography } from '@mui/material'
import AddRounded from '@mui/icons-material/AddRounded'
import type { DocumentEvaluationRun } from '../../api/platformApi'
import { evaluationComparisonRuns, evaluationExecutionFacts, evaluationVariantLabel, pairedEvaluationCases, scoredMetric } from './evaluationComparison'

const labels: Record<string, string> = { faithfulness: 'Supported by excerpts', relevance: 'Answers the question',
  referenceAgreement: 'Agrees with reference', contextPrecision: 'Relevant excerpts', contextRecall: 'Reference coverage',
  recallAtK: 'Known relevant results found', reciprocalRank: 'First relevant result', ndcgAtK: 'Ranking quality (reviewed labels)' }

export function SearchExecutionSummary({ item }: { item: DocumentEvaluationRun['cases'][number] }) {
  const facts = evaluationExecutionFacts(item)
  return <Typography variant="caption" color={facts.degraded ? 'warning.main' : 'text.secondary'}>
    {facts.searchLabel} / {facts.rankingLabel}{facts.inputTokens === null ? '' : ` / ${facts.inputTokens.toLocaleString()} billed reranking input tokens`}
  </Typography>
}

export type ExpectedEvidenceAction = (run: DocumentEvaluationRun, item: DocumentEvaluationRun['cases'][number], excerpt: { id: string; sourceId: string; content: string }) => void
export function SearchEvaluationComparison({ selected, history, onExpectedEvidence, canUseExpectedEvidence }: {
  selected: DocumentEvaluationRun; history: DocumentEvaluationRun[]; onExpectedEvidence?: ExpectedEvidenceAction
  canUseExpectedEvidence?: (...args: Parameters<ExpectedEvidenceAction>) => boolean
}) {
  const runs = evaluationComparisonRuns(selected, history)
  if (runs.length < 2) return <Alert severity="info">No matching completed comparison. Run another search variant with the same questions, source version, models and release.</Alert>
  const rows = pairedEvaluationCases(runs)
  const columns = { xs: 'minmax(0, 1fr)', md: `repeat(${runs.length}, minmax(0, 1fr))` }
  return <Box component="section" aria-label="Search quality comparison" sx={{ borderTop: 1, borderBottom: 1, borderColor: 'divider', py: 2 }}>
    <Typography variant="subtitle1" fontWeight={650} sx={{ mb: 1 }}>Compare search results</Typography>
    <Typography variant="body2" color="text.secondary" sx={{ mb: 2 }}>Matched source, questions, models and scoring method. Each result retains its own usage and evidence.</Typography>
    <Box sx={{ display: 'grid', gridTemplateColumns: columns, gap: 2 }}>
      {runs.map(run => <Box key={run.id} sx={{ minWidth: 0, borderLeft: 3, borderColor: run.id === selected.id ? 'primary.main' : 'divider', pl: 1.5 }}>
        <Typography fontWeight={650}>{evaluationVariantLabel[run.retrievalVariant ?? 'CONFIGURED']}</Typography>
        <Typography variant="caption" color="text.secondary">{new Date(run.createdAt).toLocaleString()}</Typography>
        <Stack direction="row" spacing={.5} useFlexGap flexWrap="wrap" sx={{ mt: .5 }}>
          <Chip size="small" variant="outlined" label={`${run.modelCalls} answer/scoring calls`} />
          <Chip size="small" variant="outlined" label={run.providerId} />
        </Stack>
      </Box>)}
    </Box>
    {rows.map(row => <Box key={row.id} sx={{ borderTop: 1, borderColor: 'divider', pt: 2, mt: 2 }}>
      <Typography fontWeight={650} sx={{ mb: 1 }}>{row.question}</Typography>
      <Box sx={{ display: 'grid', gridTemplateColumns: columns, gap: 2 }}>
        {runs.map((run, index) => {
          const item = row.cases[index]
          return <Box key={run.id} sx={{ minWidth: 0, overflowWrap: 'anywhere' }}>
            <Typography variant="caption" color="text.secondary" sx={{ display: { xs: 'block', md: 'none' } }}>{evaluationVariantLabel[run.retrievalVariant ?? 'CONFIGURED']}</Typography>
            {!item ? <Typography variant="body2" color="text.secondary">No matching question</Typography> : <Stack spacing={.75}>
              <SearchExecutionSummary item={item} />
              <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{item.answer ?? 'No answer recorded'}</Typography>
              <Typography variant="caption" color="text.secondary">{item.retrieval ? `${item.retrieval.evidenceCount} excerpts retrieved` : 'Retrieval unavailable'} / {item.referenceReviewed ? 'Reviewed reference' : 'Unreviewed reference'}</Typography>
              {Object.entries(item.evaluation?.metrics ?? {}).map(([name, metric]) => {
                const score = scoredMetric(metric)
                return <Box key={name}><Typography variant="body2">{labels[name] ?? name}: <strong>{score === null ? 'Not scored' : `${score.toFixed(2)} / 1`}</strong></Typography>
                  <Typography variant="caption" color="text.secondary">{metric.explanation}</Typography></Box>
              })}
              <Box component="details"><Typography component="summary" variant="body2" sx={{ cursor: 'pointer' }}>Evidence and usage</Typography>
                {item.evaluatedExcerpts?.map(excerpt => <Box key={`${excerpt.sourceId}:${excerpt.id}`} sx={{ borderLeft: 2, borderColor: 'divider', pl: 1, my: 1 }}>
                  <Typography variant="caption" color="text.secondary">{excerpt.sourceId} / {excerpt.id}</Typography>
                  <Typography variant="body2" sx={{ whiteSpace: 'pre-wrap' }}>{excerpt.content}</Typography>
                  {onExpectedEvidence && canUseExpectedEvidence?.(run, item, excerpt) && <Button size="small" startIcon={<AddRounded />} onClick={() => onExpectedEvidence(run, item, excerpt)}>Use as expected evidence</Button>}
                </Box>)}
                <Box component="pre" sx={{ fontSize: 12, overflow: 'auto', maxHeight: 200 }}>{JSON.stringify({ retrieval: item.retrieval?.diagnostics, usage: run.usage }, null, 2)}</Box>
              </Box>
            </Stack>}
          </Box>
        })}
      </Box>
    </Box>)}
  </Box>
}
